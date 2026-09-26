package com.allmusicgui.client.playlist;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.allmusicgui.AllMusicGUI;
import com.allmusicgui.model.Song;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 多本地歌单 + 同步歌单缓存，持久化到 config/allmusic-gui/playlists.json。
 * 旧版单歌单（localSongs）数据会自动迁移到默认「喜欢」歌单。
 */
public class PlaylistStore {
	/** 本地歌单 */
	public static class LocalPlaylist {
		public String id = "";
		public String name = "";
		public List<Song> songs = new ArrayList<>();
	}

	public static class SyncedPlaylist {
		public long id;
		public String name = "";
		public String description = "";
		public List<Song> songs = new ArrayList<>();
	}

	public static class Data {
		/** 多歌单结构（v2） */
		public List<LocalPlaylist> localPlaylists = new ArrayList<>();
		/** 旧版单歌单字段，仅用于迁移 */
		public List<Song> localSongs = new ArrayList<>();
		public Map<String, SyncedPlaylist> synced = new LinkedHashMap<>();
		// 网易云账号
		public String cookie = "";
		public String nickname = "";
		public long uid = 0;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String LIKE_ID = "like";

	private final Path file;
	private final Data data;

	public PlaylistStore() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve("allmusic-gui");
		this.file = dir.resolve("playlists.json");
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			AllMusicGUI.LOGGER.error("[AllMusicGUI] 创建配置目录失败", e);
		}
		this.data = load();
	}

	private Data load() {
		if (Files.isReadable(file)) {
			try {
				String json = Files.readString(file, StandardCharsets.UTF_8);
				Data d = GSON.fromJson(json, Data.class);
				if (d != null) {
					if (d.localPlaylists == null) d.localPlaylists = new ArrayList<>();
					if (d.synced == null) d.synced = new LinkedHashMap<>();
					// 迁移：旧版单歌单 → 默认「喜欢」歌单
					if (d.localSongs != null && !d.localSongs.isEmpty() && d.localPlaylists.isEmpty()) {
						LocalPlaylist like = new LocalPlaylist();
						like.id = LIKE_ID;
						like.name = "喜欢";
						like.songs = new ArrayList<>(d.localSongs);
						d.localPlaylists.add(like);
						d.localSongs = new ArrayList<>();
						save();
					}
					return d;
				}
			} catch (Exception e) {
				AllMusicGUI.LOGGER.warn("[AllMusicGUI] 本地歌单读取失败，使用空数据", e);
			}
		}
		return new Data();
	}

	private void save() {
		try {
			Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
		} catch (Exception e) {
			AllMusicGUI.LOGGER.error("[AllMusicGUI] 本地歌单保存失败", e);
		}
	}

	// ---------- 本地多歌单 ----------

	/** 确保默认「喜欢」歌单存在 */
	private void ensureDefault() {
		if (data.localPlaylists.isEmpty()) {
			LocalPlaylist like = new LocalPlaylist();
			like.id = LIKE_ID;
			like.name = "喜欢";
			data.localPlaylists.add(like);
			save();
		}
	}

	/** 全部本地歌单（保证至少含「喜欢」） */
	public List<LocalPlaylist> localPlaylists() {
		ensureDefault();
		return data.localPlaylists;
	}

	public LocalPlaylist byId(String id) {
		for (LocalPlaylist p : data.localPlaylists) {
			if (p.id.equals(id)) return p;
		}
		return null;
	}

	/** 新建歌单（自动去重命名），返回创建的歌单 */
	public LocalPlaylist createPlaylist(String name) {
		ensureDefault();
		String base = (name == null || name.isBlank()) ? "新歌单" : name.trim();
		LocalPlaylist pl = new LocalPlaylist();
		pl.id = "pl-" + System.currentTimeMillis() + "-" + data.localPlaylists.size();
		pl.name = uniqueName(base);
		data.localPlaylists.add(pl);
		save();
		return pl;
	}

	private String uniqueName(String base) {
		for (LocalPlaylist p : data.localPlaylists) {
			if (p.name.equals(base)) {
				int n = 2;
				while (true) {
					String cand = base + " (" + n + ")";
					boolean ok = true;
					for (LocalPlaylist x : data.localPlaylists) {
						if (x.name.equals(cand)) { ok = false; break; }
					}
					if (ok) return cand;
					n++;
				}
			}
		}
		return base;
	}

	/** 把歌加入指定歌单（去重） */
	public void addToPlaylist(String id, Song s) {
		LocalPlaylist pl = byId(id);
		if (pl == null) return;
		for (Song x : pl.songs) {
			if (x.id == s.id && x.type.equals(s.type)) return;
		}
		pl.songs.add(s);
		save();
	}

	public void removeFromPlaylist(String id, Song s) {
		LocalPlaylist pl = byId(id);
		if (pl == null) return;
		pl.songs.removeIf(x -> x.id == s.id && x.type.equals(s.type));
		save();
	}

	public void clearPlaylist(String id) {
		LocalPlaylist pl = byId(id);
		if (pl == null) return;
		pl.songs.clear();
		save();
	}

	/** 删除歌单（默认「喜欢」不可删除） */
	public void removePlaylist(String id) {
		if (LIKE_ID.equals(id)) return;
		data.localPlaylists.removeIf(p -> p.id.equals(id));
		save();
	}

	// ---------- 旧版兼容入口（默认「喜欢」歌单） ----------

	public void addLocal(Song s) {
		ensureDefault();
		addToPlaylist(LIKE_ID, s);
	}

	public void removeLocal(Song s) {
		ensureDefault();
		removeFromPlaylist(LIKE_ID, s);
	}

	public void clearLocal() {
		ensureDefault();
		LocalPlaylist like = byId(LIKE_ID);
		if (like != null) like.songs.clear();
		save();
	}

	// ---------- 同步歌单 ----------

	public void saveSynced(SyncedPlaylist pl) {
		data.synced.put(String.valueOf(pl.id), pl);
		save();
	}

	public void removeSynced(long id) {
		data.synced.remove(String.valueOf(id));
		save();
	}

	public List<SyncedPlaylist> syncedPlaylists() {
		return new ArrayList<>(data.synced.values());
	}

	// ---------- 账号 ----------

	public String cookie() { return data.cookie == null ? "" : data.cookie; }
	public String nickname() { return data.nickname == null ? "" : data.nickname; }
	public long uid() { return data.uid; }

	public void saveAccount(String cookie, String nickname, long uid) {
		data.cookie = cookie;
		data.nickname = nickname;
		data.uid = uid;
		save();
	}

	public void clearAccount() {
		data.cookie = "";
		data.nickname = "";
		data.uid = 0;
		save();
	}
}
