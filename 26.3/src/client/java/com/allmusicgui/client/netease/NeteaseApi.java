package com.allmusicgui.client.netease;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.allmusicgui.AllMusicGUI;
import com.allmusicgui.model.SearchType;
import com.allmusicgui.model.Song;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 网易云音乐非官方 API 客户端（搜索 / 歌单详情 / 账号 / 我的歌单）。
 * 使用 JDK 自带 HttpClient + Minecraft 自带的 Gson，无额外依赖。
 * 请求头对齐原版 AllMusic：桌面 Chrome UA + Referer。
 */
public final class NeteaseApi {
	private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0";

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	/** 已保存的登录 Cookie（主要是 MUSIC_U），可被 PlaylistStore 载入 */
	private static volatile String cookie = "os=pc; appver=8.9.70";

	public static void setCookie(String c) {
		cookie = (c == null || c.isBlank()) ? "os=pc; appver=8.9.70" : c;
	}

	public static String getCookie() {
		return cookie;
	}

	/** 一次接口调用的结果：成功 + 歌曲列表，或失败 + 错误信息 */
	public record NeteaseResult(boolean ok, String error, List<Song> songs) {
		public static NeteaseResult fail(String msg) {
			return new NeteaseResult(false, msg, List.of());
		}

		public static NeteaseResult ok(List<Song> songs) {
			return new NeteaseResult(true, "", songs);
		}
	}

	/** 登录账号信息 */
	public record Account(boolean ok, String error, long uid, String nickname) {
		public static Account fail(String msg) {
			return new Account(false, msg, 0, "");
		}
	}

	private NeteaseApi() {
	}

	// ---------- 搜索 / 歌单 ----------

	/** 搜索：keyword 可为名称/歌手/歌单名，type 决定结果类型 */
	public static CompletableFuture<NeteaseResult> search(String keyword, SearchType type, int limit) {
		String url = "https://music.163.com/api/search/get/web?s=" + enc(keyword)
				+ "&type=" + type.neteaseType + "&limit=" + limit + "&offset=0";
		return getJson(url).thenApply(json -> {
			try {
				JsonObject root = JsonParser.parseString(json).getAsJsonObject();
				int code = root.has("code") ? root.get("code").getAsInt() : -1;
				if (code != 200) return NeteaseResult.fail("接口返回 code=" + code);
				JsonObject result = root.getAsJsonObject("result");
				if (result == null) return NeteaseResult.ok(new ArrayList<>());
				List<Song> out = new ArrayList<>();
				switch (type) {
					case SONG -> collectSongs(result.getAsJsonArray("songs"), out);
					case ARTIST -> collectArtists(result.getAsJsonArray("artists"), out);
					case ALBUM -> collectAlbums(result.getAsJsonArray("albums"), out);
					case PLAYLIST -> collectPlaylists(result.getAsJsonArray("playlists"), out);
				}
				return NeteaseResult.ok(out);
			} catch (Exception e) {
				AllMusicGUI.LOGGER.warn("[AllMusicGUI] 搜索结果解析失败", e);
				return NeteaseResult.fail("返回内容解析失败");
			}
		});
	}

	/** 网易云歌单详情（含歌曲列表） */
	public static CompletableFuture<NeteaseResult> playlist(long id) {
		String url = "https://music.163.com/api/v6/playlist/detail?id=" + id;
		return getJson(url).thenApply(json -> {
			try {
				JsonObject root = JsonParser.parseString(json).getAsJsonObject();
				int code = root.has("code") ? root.get("code").getAsInt() : -1;
				if (code != 200) return NeteaseResult.fail("接口返回 code=" + code);
				JsonObject pl = root.getAsJsonObject("playlist");
				if (pl == null) return NeteaseResult.fail("歌单不存在或已下架");
				List<Song> out = new ArrayList<>();
				collectSongs(pl.getAsJsonArray("tracks"), out);
				return NeteaseResult.ok(out);
			} catch (Exception e) {
				AllMusicGUI.LOGGER.warn("[AllMusicGUI] 歌单解析失败", e);
				return NeteaseResult.fail("返回内容解析失败");
			}
		});
	}

	/** 歌手的热门歌曲（二级菜单下钻用） */
	public static CompletableFuture<NeteaseResult> artistSongs(long id) {
		String url = "https://music.163.com/api/v1/artist/songs?id=" + id
				+ "&limit=100&offset=0&order=hot";
		return getJson(url).thenApply(json -> {
			try {
				JsonObject root = JsonParser.parseString(json).getAsJsonObject();
				int code = root.has("code") ? root.get("code").getAsInt() : -1;
				if (code != 200) return NeteaseResult.fail("接口返回 code=" + code);
				List<Song> out = new ArrayList<>();
				collectSongs(root.getAsJsonArray("songs"), out);
				return NeteaseResult.ok(out);
			} catch (Exception e) {
				AllMusicGUI.LOGGER.warn("[AllMusicGUI] 歌手歌曲解析失败", e);
				return NeteaseResult.fail("返回内容解析失败");
			}
		});
	}

	/** 专辑详情（含歌曲列表，二级菜单下钻用） */
	public static CompletableFuture<NeteaseResult> album(long id) {
		String url = "https://music.163.com/api/v1/album/" + id;
		return getJson(url).thenApply(json -> {
			try {
				JsonObject root = JsonParser.parseString(json).getAsJsonObject();
				int code = root.has("code") ? root.get("code").getAsInt() : -1;
				if (code != 200) return NeteaseResult.fail("接口返回 code=" + code);
				List<Song> out = new ArrayList<>();
				collectSongs(root.getAsJsonArray("songs"), out);
				if (out.isEmpty()) {
					JsonObject album = root.getAsJsonObject("album");
					if (album != null) collectSongs(album.getAsJsonArray("songs"), out);
				}
				return NeteaseResult.ok(out);
			} catch (Exception e) {
				AllMusicGUI.LOGGER.warn("[AllMusicGUI] 专辑解析失败", e);
				return NeteaseResult.fail("返回内容解析失败");
			}
		});
	}

	// ---------- 账号 / 我的歌单 ----------

	/** 用已保存的 Cookie 校验登录态，返回昵称和 uid */
	public static CompletableFuture<Account> accountInfo() {
		return getJson("https://music.163.com/api/nuser/account/get").thenApply(json -> {
			try {
				JsonObject root = JsonParser.parseString(json).getAsJsonObject();
				int code = root.has("code") ? root.get("code").getAsInt() : -1;
				if (code != 200 || !root.has("profile") || root.get("profile").isJsonNull()) {
					return Account.fail("未登录或 Cookie 已失效（code=" + code + "）");
				}
				JsonObject profile = root.getAsJsonObject("profile");
				long uid = profile.has("userId") ? profile.get("userId").getAsLong() : 0;
				String name = safe(profile, "nickname");
				return new Account(true, "", uid, name);
			} catch (Exception e) {
				AllMusicGUI.LOGGER.warn("[AllMusicGUI] 账号解析失败", e);
				return Account.fail("账号信息解析失败");
			}
		});
	}

	/** 拉取该用户创建/收藏的歌单（返回 type="歌单" 的 Song 列表，id 为歌单 id） */
	public static CompletableFuture<NeteaseResult> myPlaylists(long uid) {
		String url = "https://music.163.com/api/user/playlist?uid=" + uid + "&limit=100&offset=0";
		return getJson(url).thenApply(json -> {
			try {
				JsonObject root = JsonParser.parseString(json).getAsJsonObject();
				int code = root.has("code") ? root.get("code").getAsInt() : -1;
				if (code != 200) return NeteaseResult.fail("接口返回 code=" + code);
				List<Song> out = new ArrayList<>();
				collectPlaylists(root.getAsJsonArray("playlist"), out);
				return NeteaseResult.ok(out);
			} catch (Exception e) {
				AllMusicGUI.LOGGER.warn("[AllMusicGUI] 我的歌单解析失败", e);
				return NeteaseResult.fail("返回内容解析失败");
			}
		});
	}

	// ---------- 解析 ----------

	private static void collectSongs(JsonArray arr, List<Song> out) {
		if (arr == null) return;
		for (JsonElement e : arr) {
			try {
				JsonObject o = e.getAsJsonObject();
				if (!o.has("id") || o.get("id").isJsonNull()) continue;
				long id = o.get("id").getAsLong();
				String name = safe(o, "name");
				String artist = joinNames(o.getAsJsonArray("ar"));
				if (artist.isEmpty()) artist = joinNames(o.getAsJsonArray("artists"));
				String album = "";
				JsonArray al = o.getAsJsonArray("al");
				if (al != null && !al.isEmpty()) {
					album = safe(al.get(0).getAsJsonObject(), "name");
				} else if (o.has("album") && !o.get("album").isJsonNull()) {
					album = safe(o.getAsJsonObject("album"), "name");
				}
				int dur = 0;
				if (o.has("duration") && !o.get("duration").isJsonNull()) {
					dur = (int) (o.get("duration").getAsLong() / 1000);
				} else if (o.has("dt") && !o.get("dt").isJsonNull()) {
					dur = (int) (o.get("dt").getAsLong() / 1000);
				}
				String sub = artist.isEmpty() ? album : (album.isEmpty() ? artist : artist + " - " + album);
				out.add(new Song(id, name, sub, "单曲", dur));
			} catch (Exception ignore) {
			}
		}
	}

	private static void collectArtists(JsonArray arr, List<Song> out) {
		if (arr == null) return;
		for (JsonElement e : arr) {
			try {
				JsonObject o = e.getAsJsonObject();
				long id = o.get("id").getAsLong();
				String name = safe(o, "name");
				int size = o.has("albumSize") ? o.get("albumSize").getAsInt() : 0;
				out.add(new Song(id, name, "专辑数 " + size, "歌手", 0));
			} catch (Exception ignore) {
			}
		}
	}

	private static void collectAlbums(JsonArray arr, List<Song> out) {
		if (arr == null) return;
		for (JsonElement e : arr) {
			try {
				JsonObject o = e.getAsJsonObject();
				long id = o.get("id").getAsLong();
				String name = safe(o, "name");
				String artist = "";
				if (o.has("artist") && !o.get("artist").isJsonNull()) {
					artist = safe(o.getAsJsonObject("artist"), "name");
				}
				out.add(new Song(id, name, artist, "专辑", 0));
			} catch (Exception ignore) {
			}
		}
	}

	private static void collectPlaylists(JsonArray arr, List<Song> out) {
		if (arr == null) return;
		for (JsonElement e : arr) {
			try {
				JsonObject o = e.getAsJsonObject();
				long id = o.get("id").getAsLong();
				String name = safe(o, "name");
				String desc = safe(o, "description");
				int count = o.has("trackCount") ? o.get("trackCount").getAsInt() : 0;
				String sub = desc == null || desc.isEmpty() ? count + " 首" : desc;
				out.add(new Song(id, name, sub, "歌单", 0));
			} catch (Exception ignore) {
			}
		}
	}

	// ---------- 工具 ----------

	private static String safe(JsonObject o, String key) {
		try {
			JsonElement e = o.get(key);
			if (e == null || e.isJsonNull()) return "";
			return e.getAsString();
		} catch (Exception e) {
			return "";
		}
	}

	private static String joinNames(JsonArray arr) {
		if (arr == null || arr.isEmpty()) return "";
		StringBuilder sb = new StringBuilder();
		for (JsonElement e : arr) {
			if (sb.length() > 0) sb.append(" / ");
			sb.append(safe(e.getAsJsonObject(), "name"));
		}
		return sb.toString();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static CompletableFuture<String> getJson(String url) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				HttpRequest req = HttpRequest.newBuilder(URI.create(url))
						.header("User-Agent", UA)
						.header("Referer", "https://music.163.com/")
						.header("Cookie", cookie)
						.header("Accept", "application/json, text/plain, */*")
						.GET()
						.build();
				HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (resp.statusCode() != 200) {
					return "{\"code\":" + resp.statusCode() + "}";
				}
				return resp.body();
			} catch (Exception e) {
				AllMusicGUI.LOGGER.error("[AllMusicGUI] 网易云 API 请求失败: " + url, e);
				return "{\"error\":\"" + e.getClass().getSimpleName() + "\",\"code\":-1}";
			}
		});
	}
}
