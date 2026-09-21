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

/** 本地歌单 + 同步歌单缓存，持久化到 config/allmusic-gui/playlists.json */
public class PlaylistStore {
	public static class SyncedPlaylist {
		public long id;
		public String name = "";
		public String description = "";
		public List<Song> songs = new ArrayList<>();
	}

	public static class Data {
		public List<Song> localSongs = new ArrayList<>();
		public Map<String, SyncedPlaylist> synced = new LinkedHashMap<>();
		// 网易云账号
		public String cookie = "";
		public String nickname = "";
		public long uid = 0;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
					if (d.localSongs == null) d.localSongs = new ArrayList<>();
					if (d.synced == null) d.synced = new LinkedHashMap<>();
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

	public List<Song> localSongs() {
		return data.localSongs;
	}

	public void addLocal(Song s) {
		for (Song x : data.localSongs) {
			if (x.id == s.id && x.type.equals(s.type)) return;
		}
		data.localSongs.add(s);
		save();
	}

	public void removeLocal(Song s) {
		data.localSongs.removeIf(x -> x.id == s.id && x.type.equals(s.type));
		save();
	}

	public void clearLocal() {
		data.localSongs.clear();
		save();
	}

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
