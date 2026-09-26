package com.allmusicgui.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.allmusicgui.client.MusicCommand;
import com.allmusicgui.client.netease.NeteaseApi;
import com.allmusicgui.client.playlist.PlaylistStore;
import com.allmusicgui.model.SearchType;
import com.allmusicgui.model.Song;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * AllMusic 点歌界面（1.21.1 旧渲染：GuiGraphics 直接绘制）。
 * 顶部输入框；中间内容；底部四页签：搜索 / 歌单(本地多歌单) / 云歌单(同步) / 我的。
 * - 搜索歌手/专辑/歌单后可下钻（二级菜单，ESC 返回）
 * - 左键点歌；右键单曲弹出歌单选择菜单收藏；右键歌手/专辑/歌单直接保存为本地歌单
 * - 本地歌单为多歌单，默认「喜欢」歌单，可新建/删除
 */
public class AllMusicGuiScreen extends Screen {
	public enum Tab { SEARCH, LOCAL, PLAYLIST, MINE }

	private static final int CONTENT_W = 460;
	private static final int TAB_H = 24;
	private static final int GLFW_KEY_ENTER = 257;
	private static final int GLFW_KEY_KP_ENTER = 335;
	private static final int GLFW_KEY_ESCAPE = 256;
	private static final int GLFW_KEY_UP = 265;
	private static final int GLFW_KEY_DOWN = 264;
	private static final int GLFW_KEY_DELETE = 261;
	private static final int GLFW_KEY_BACKSPACE = 259;
	private static final int LEFT_COL_W = 250;
	private static final int POPUP_W = 300;
	private static final int POPUP_MAX_ROWS = 8;

	private final PlaylistStore store = new PlaylistStore();
	private Tab tab = Tab.SEARCH;
	private String status = "左键点歌 / 右键收藏（多歌单）";

	private String keyword = "";
	private String playlistId = "";
	private SearchType searchType = SearchType.SONG;

	private boolean closeAfterPlay = true;
	private boolean overwriteSync = true;
	private boolean loginMode = false;
	private final List<Song> myPlaylists = new ArrayList<>();
	private boolean autoTried = false;

	private int yMyCloudLabel;
	private int ySyncedLabel;
	private int ySyncedListTop;

	/** 搜索结果（歌手/专辑/歌单/单曲），切页签后保留 */
	private final List<Song> searchResults = new ArrayList<>();
	/** 下钻后的歌曲列表（点歌手/专辑/歌单进入） */
	private final List<Song> drillSongs = new ArrayList<>();
	private boolean drilling = false;
	private boolean hasSearched = false;
	/** 搜索竞态防护：只有最新一次搜索的响应会被应用 */
	private int searchSeq = 0;

	private final List<Song> displaySongs = new ArrayList<>();
	private String listTitle = "搜索结果";
	private Song lastClicked;

	// 多歌单状态
	/** LOCAL 页当前打开的歌单 id；null = 歌单列表一级 */
	private String currentPlaylistId;
	/** 右键单曲后的歌单选择弹窗 */
	private boolean pickingPlaylist = false;
	private Song pendingSong;
	/** 新建歌单命名模式 */
	private boolean namingPlaylist = false;
	private String pendingName = "";

	private EditBox inputBox;
	private EditBox cookieBox;
	private EditBox nameBox;
	private SongListWidget list;
	private final List<Button> typeButtons = new ArrayList<>();

	public AllMusicGuiScreen() {
		super(Component.literal("AllMusic 点歌"));
	}

	private boolean isLoggedIn() {
		return store.uid() != 0 && !store.cookie().isEmpty();
	}

	@Override
	protected void init() {
		clearWidgets();
		if (!store.cookie().isEmpty()) {
			NeteaseApi.setCookie(store.cookie());
		}
		int cx = width / 2;
		int lx = cx - CONTENT_W / 2;

		boolean needInput = (tab == Tab.SEARCH || tab == Tab.PLAYLIST);
		if (needInput) {
			inputBox = new EditBox(font, lx + 40, 12, CONTENT_W - 80, 20, Component.literal("输入"));
			inputBox.setMaxLength(128);
			addRenderableWidget(inputBox);
		} else {
			inputBox = null;
		}

		switch (tab) {
			case SEARCH -> {
				inputBox.setValue(keyword);
				inputBox.setHint(Component.literal("搜索歌名 / 歌手 / 专辑 / 歌单…"));
				if (!drilling && !pickingPlaylist && !namingPlaylist) {
					setInitialFocus(inputBox);
				}
				typeButtons.clear();
				if (!pickingPlaylist && !namingPlaylist) {
					SearchType[] types = SearchType.values();
					for (int i = 0; i < types.length; i++) {
						final SearchType st = types[i];
						Button b = Button.builder(Component.literal(st.label), btn -> {
									searchType = st;
									refreshTypeButtons();
									doSearch();
								})
								.bounds(lx + i * 80, 38, 76, 18).build();
						typeButtons.add(b);
						addRenderableWidget(b);
					}
					refreshTypeButtons();
				}
				if (drilling) {
					addRenderableWidget(Button.builder(Component.literal("← 返回"), b -> backToSearch())
							.bounds(lx + CONTENT_W - 60, 38, 60, 18).build());
				}
				listTitle = drilling ? "结果" : "搜索结果";
			}
			case LOCAL -> {
				buildLocalPage(lx);
			}
			case PLAYLIST -> {
				inputBox.setValue(playlistId);
				inputBox.setHint(Component.literal("输入网易云歌单 ID（数字）"));
				if (!pickingPlaylist && !namingPlaylist) {
					setInitialFocus(inputBox);
				}
				addRenderableWidget(Button.builder(Component.literal("同步"), b -> doSync())
						.bounds(lx + CONTENT_W - 90, 38, 86, 18).build());
				listTitle = "云歌单（输入 ID 同步）";
			}
			case MINE -> {
				buildMinePage(lx);
				return;
			}
		}

		if (tab == Tab.SEARCH) {
			list = new SongListWidget(font, this::onSongClick);
			list.setBounds(lx, 64, CONTENT_W, Math.max(80, height - 64 - TAB_H - 38));
			displaySongs.clear();
			displaySongs.addAll(drilling ? drillSongs : searchResults);
			list.setSongs(displaySongs);
		} else if (tab == Tab.LOCAL) {
			list = new SongListWidget(font, this::onSongClick);
			list.setBounds(lx, 64, CONTENT_W, Math.max(80, height - 64 - TAB_H - 38));
			displaySongs.clear();
			if (currentPlaylistId == null) {
				// 一级：歌单卡片列表（伪 Song）
				List<PlaylistStore.LocalPlaylist> pls = store.localPlaylists();
				for (int i = 0; i < pls.size(); i++) {
					PlaylistStore.LocalPlaylist p = pls.get(i);
					displaySongs.add(new Song(i, p.name, p.songs.size() + " 首", "本地歌单", 0));
				}
			} else {
				PlaylistStore.LocalPlaylist pl = store.byId(currentPlaylistId);
				if (pl != null) displaySongs.addAll(pl.songs);
			}
			list.setSongs(displaySongs);
		} else if (tab == Tab.PLAYLIST) {
			list = new SongListWidget(font, this::onSongClick);
			list.setBounds(lx, 64, CONTENT_W, Math.max(80, height - 64 - TAB_H - 38));
			displaySongs.clear();
			list.setSongs(displaySongs);
		}

		if (pickingPlaylist || namingPlaylist) {
			buildPopup();
		}

		addTabButtons(lx);
	}

	private int mineTop() { return 64; }
	private int mineBottom() { return height - TAB_H - 38; }

	private void buildLocalPage(int lx) {
		if (namingPlaylist) return;
		if (currentPlaylistId == null) {
			addRenderableWidget(Button.builder(Component.literal("新建歌单"), b -> {
						namingPlaylist = true;
						pendingSong = null;
						pendingName = "";
						pickingPlaylist = false;
						init();
					})
					.bounds(lx + CONTENT_W - 90, 38, 86, 18).build());
			listTitle = "本地歌单（" + store.localPlaylists().size() + " 个）";
		} else {
			PlaylistStore.LocalPlaylist pl = store.byId(currentPlaylistId);
			addRenderableWidget(Button.builder(Component.literal("← 返回"), b -> {
						currentPlaylistId = null;
						init();
					})
					.bounds(lx + CONTENT_W - 296, 38, 66, 18).build());
			addRenderableWidget(Button.builder(Component.literal("移除选中"), b -> removeSelectedFromLocal())
					.bounds(lx + CONTENT_W - 224, 38, 86, 18).build());
			addRenderableWidget(Button.builder(Component.literal("清空"), b -> {
						if (pl != null) store.clearPlaylist(pl.id);
						status = "已清空歌单";
						init();
					})
					.bounds(lx + CONTENT_W - 132, 38, 56, 18).build());
			if (!"like".equals(currentPlaylistId)) {
				addRenderableWidget(Button.builder(Component.literal("删除"), b -> {
							store.removePlaylist(currentPlaylistId);
							currentPlaylistId = null;
							status = "已删除歌单";
							init();
						})
						.bounds(lx + CONTENT_W - 70, 38, 66, 18).build());
			}
			listTitle = pl != null ? pl.name + "（" + pl.songs.size() + " 首）" : "本地歌单";
		}
	}

	private void buildMinePage(int lx) {
		int top = mineTop();
		int bottom = mineBottom();
		int leftX = lx + 4;
		int leftW = LEFT_COL_W - 8;

		addRenderableWidget(Button.builder(
						Component.literal(isLoggedIn() ? "退出登录" : (loginMode ? "取消登录" : "点击登录")), b -> {
							if (isLoggedIn()) {
								store.clearAccount();
								NeteaseApi.setCookie(null);
								myPlaylists.clear();
								status = "已退出网易云登录";
							} else {
								loginMode = !loginMode;
							}
							init();
						})
				.bounds(leftX + 52, top + 14, 90, 18).build());

		list = new SongListWidget(font, this::onSongClick);
		list.setBounds(leftX, top + 60, leftW, Math.max(60, bottom - top - 60));
		displaySongs.clear();
		PlaylistStore.LocalPlaylist like = store.byId("like");
		if (like != null) displaySongs.addAll(like.songs);
		list.setSongs(displaySongs);

		int rx = lx + LEFT_COL_W + 10;
		int rw = CONTENT_W - LEFT_COL_W - 10 - 8;
		int y = top + 38;

		if (loginMode && !isLoggedIn()) {
			cookieBox = new EditBox(font, rx + 4, y, rw - 8, 18, Component.literal("MUSIC_U Cookie"));
			cookieBox.setMaxLength(512);
			cookieBox.setHint(Component.literal("粘贴 music.163.com 的 Cookie（含 MUSIC_U）"));
			addRenderableWidget(cookieBox);
			y += 24;
			addRenderableWidget(Button.builder(Component.literal("验证并登录"), b -> doLogin(cookieBox.getValue().trim()))
					.bounds(rx + 4, y, (rw - 12) / 2, 18).build());
			addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
						loginMode = false;
						init();
					})
					.bounds(rx + 8 + (rw - 12) / 2, y, (rw - 12) / 2, 18).build());
			y += 24;
		}

		yMyCloudLabel = y;
		y += 16;
		if (isLoggedIn()) {
			addRenderableWidget(Button.builder(Component.literal("拉取我的云歌单"), b -> loadMyPlaylists())
					.bounds(rx + 4, y, rw - 8, 18).build());
			y += 22;
			for (Song pl : myPlaylists) {
				final long pid = pl.id;
				final String pname = pl.name;
				addRenderableWidget(Button.builder(Component.literal("▸ " + pname), b -> openPlaylist(pid, pname))
						.bounds(rx + 4, y, rw - 64, 18).build());
				addRenderableWidget(Button.builder(Component.literal("同步"), b -> openPlaylist(pid, pname))
						.bounds(rx + rw - 56, y, 56, 18).build());
				y += 20;
			}
			y += 6;
			if (myPlaylists.isEmpty() && !autoTried) {
				autoTried = true;
				loadMyPlaylists();
			}
		}

		ySyncedLabel = y;
		y += 14;
		ySyncedListTop = y;
		List<PlaylistStore.SyncedPlaylist> synced = store.syncedPlaylists();
		for (PlaylistStore.SyncedPlaylist sp : synced) {
			final long id = sp.id;
			final String nm = sp.name;
			addRenderableWidget(Button.builder(Component.literal("移除"), b -> {
						store.removeSynced(id);
						status = "已移除同步歌单：" + nm;
						init();
					})
					.bounds(rx + rw - 60, y, 60, 18).build());
			y += 22;
		}
		y += 8;

		addRenderableWidget(Button.builder(Component.literal(toggleText(closeAfterPlay, "点歌后自动关闭")), b -> {
					closeAfterPlay = !closeAfterPlay;
					init();
				})
				.bounds(rx + 4, y, rw - 8, 18).build());
		y += 24;
		addRenderableWidget(Button.builder(Component.literal(toggleText(overwriteSync, "同步覆盖同名歌单")), b -> {
					overwriteSync = !overwriteSync;
					init();
				})
				.bounds(rx + 4, y, rw - 8, 18).build());

		addTabButtons(lx);
	}

	private void addTabButtons(int lx) {
		int tabW = CONTENT_W / 4;
		int ty = height - TAB_H - 8;
		addRenderableWidget(Button.builder(Component.literal("搜索"), b -> setTab(Tab.SEARCH))
				.bounds(lx, ty, tabW, TAB_H).build());
		addRenderableWidget(Button.builder(Component.literal("歌单"), b -> setTab(Tab.LOCAL))
				.bounds(lx + tabW, ty, tabW, TAB_H).build());
		addRenderableWidget(Button.builder(Component.literal("云歌单"), b -> setTab(Tab.PLAYLIST))
				.bounds(lx + 2 * tabW, ty, tabW, TAB_H).build());
		addRenderableWidget(Button.builder(Component.literal("我的"), b -> setTab(Tab.MINE))
				.bounds(lx + 3 * tabW, ty, tabW, TAB_H).build());
	}

	private static String toggleText(boolean on, String label) {
		return (on ? "[√] " : "[×] ") + label;
	}

	private void setTab(Tab t) {
		if (tab == t) return;
		pickingPlaylist = false;
		namingPlaylist = false;
		pendingSong = null;
		currentPlaylistId = null;
		tab = t;
		init();
	}

	private void onSongClick(Song s, int button) {
		lastClicked = s;
		if (tab == Tab.LOCAL && currentPlaylistId == null) {
			if (button == 0) {
				int idx = (int) s.id;
				List<PlaylistStore.LocalPlaylist> pls = store.localPlaylists();
				if (idx >= 0 && idx < pls.size()) {
					currentPlaylistId = pls.get(idx).id;
					init();
				}
			} else {
				status = "点击歌单进入，内部右键歌曲可移除";
			}
			return;
		}
		if (button == 1) {
			if (tab == Tab.LOCAL && currentPlaylistId != null) {
				store.removeFromPlaylist(currentPlaylistId, s);
				status = "已从歌单移除：" + s.name;
				init();
				return;
			}
			if ("单曲".equals(s.type)) {
				pendingSong = s;
				pickingPlaylist = true;
				namingPlaylist = false;
				init();
				return;
			}
			if ("歌手".equals(s.type) || "专辑".equals(s.type) || "歌单".equals(s.type)) {
				saveAsPlaylist(s);
				return;
			}
			status = "无法收藏：" + s.type;
			return;
		}
		if ("单曲".equals(s.type)) {
			MusicCommand.request(s);
			status = "已发送点歌：" + s.name + "（/music " + s.id + "）";
			if (closeAfterPlay) {
				Minecraft.getInstance().setScreen(null);
			}
		} else if ("歌手".equals(s.type)) {
			drillDown(s.id, s.name, NeteaseApi::artistSongs);
		} else if ("专辑".equals(s.type)) {
			drillDown(s.id, s.name, NeteaseApi::album);
		} else if ("歌单".equals(s.type)) {
			if (tab == Tab.PLAYLIST) {
				openPlaylist(s.id, s.name);
			} else {
				drillDown(s.id, s.name, NeteaseApi::playlist);
			}
		} else {
			status = s.type + "不可直接点歌：" + s.name;
		}
	}

	/** 右键保存：把歌手/专辑/歌单内容拉下来存为新的本地歌单 */
	private void saveAsPlaylist(Song s) {
		status = "正在保存 “" + s.name + "” 为本地歌单…";
		Function<Long, CompletableFuture<NeteaseApi.NeteaseResult>> loader;
		if ("歌手".equals(s.type)) {
			loader = NeteaseApi::artistSongs;
		} else if ("专辑".equals(s.type)) {
			loader = NeteaseApi::album;
		} else {
			loader = NeteaseApi::playlist;
		}
		loader.apply(s.id).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			if (r.ok() && !r.songs().isEmpty()) {
				PlaylistStore.LocalPlaylist pl = store.createPlaylist(s.name);
				for (Song x : r.songs()) {
					store.addToPlaylist(pl.id, x);
				}
				status = "已保存为本地歌单 “" + pl.name + "”（" + r.songs().size() + " 首）";
			} else if (r.ok()) {
				status = "保存失败：内容为空";
			} else {
				status = "保存失败：" + r.error();
			}
		}));
	}

	/** 下钻：点歌手/专辑/歌单后在搜索页内展示其歌曲列表 */
	private void drillDown(long id, String name, Function<Long, CompletableFuture<NeteaseApi.NeteaseResult>> loader) {
		drilling = true;
		drillSongs.clear();
		if (list != null) list.setSongs(drillSongs);
		listTitle = name;
		status = "正在加载 “" + name + "” …";
		loader.apply(id).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			if (r.ok()) {
				drillSongs.clear();
				drillSongs.addAll(r.songs());
				listTitle = name + "（" + r.songs().size() + " 首）";
				status = "加载完成，左键点歌/右键收藏";
			} else {
				drilling = false;
				listTitle = "搜索结果";
				status = "加载失败：" + r.error();
			}
			init();
		}));
	}

	private void backToSearch() {
		drilling = false;
		drillSongs.clear();
		listTitle = "搜索结果";
		status = "已返回搜索结果";
		init();
	}

	private void removeSelectedFromLocal() {
		Song s = list != null ? list.selectedSong() : null;
		if (s == null) {
			status = "请先选择要移除的歌曲";
			return;
		}
		store.removeFromPlaylist(currentPlaylistId, s);
		status = "已移除：" + s.name;
		init();
	}

	private void doSearch() {
		keyword = inputBox.getValue().trim();
		if (keyword.isEmpty()) {
			status = "请输入搜索关键词（歌名/歌手/专辑/歌单名）";
			return;
		}
		hasSearched = true;
		drilling = false;
		searchResults.clear();
		displaySongs.clear();
		if (list != null) list.setSongs(displaySongs);
		status = "正在搜索 “" + keyword + "” …";
		final int seq = ++searchSeq;
		NeteaseApi.search(keyword, searchType, 30).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			if (seq != searchSeq) return; // 过期响应丢弃，避免覆盖下钻内容
			if (r.ok()) {
				searchResults.clear();
				searchResults.addAll(r.songs());
				displaySongs.clear();
				displaySongs.addAll(searchResults);
				if (list != null) list.setSongs(displaySongs);
				listTitle = "搜索结果";
				status = r.songs().isEmpty() ? "没有找到结果" : "找到 " + r.songs().size() + " 条（左键打开/点歌，右键收藏）";
			} else {
				status = "搜索失败：" + r.error();
			}
		}));
	}

	private void doSync() {
		playlistId = inputBox.getValue().trim();
		long id;
		try {
			id = Long.parseLong(playlistId);
		} catch (Exception e) {
			status = "歌单 ID 格式不正确";
			return;
		}
		openPlaylist(id, "歌单 #" + id);
	}

	/** 打开网易云歌单：拉取歌曲、存为同步歌单，并切到云歌单页展示 */
	private void openPlaylist(long id, String name) {
		displaySongs.clear();
		if (list != null) list.setSongs(displaySongs);
		listTitle = name;
		status = "正在加载歌单 “" + name + "” …";
		NeteaseApi.playlist(id).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			if (r.ok()) {
				displaySongs.clear();
				displaySongs.addAll(r.songs());
				if (list != null) list.setSongs(displaySongs);
				PlaylistStore.SyncedPlaylist sp = new PlaylistStore.SyncedPlaylist();
				sp.id = id;
				sp.name = name;
				sp.songs = new ArrayList<>(r.songs());
				store.saveSynced(sp);
				listTitle = name + "（" + r.songs().size() + " 首）";
				status = "歌单加载完成，左键点歌/右键收藏";
			} else {
				status = "歌单加载失败：" + r.error();
			}
			tab = Tab.PLAYLIST;
			init();
		}));
	}

	private void doLogin(String cookie) {
		if (cookie.isEmpty()) {
			status = "请先粘贴 Cookie";
			return;
		}
		status = "正在验证登录…";
		NeteaseApi.setCookie(cookie);
		NeteaseApi.accountInfo().thenAccept(a -> Minecraft.getInstance().execute(() -> {
			if (a.ok()) {
				store.saveAccount(cookie, a.nickname(), a.uid());
				loginMode = false;
				autoTried = false;
				status = "登录成功：" + a.nickname() + "，正在同步云歌单…";
				loadMyPlaylists();
			} else {
				NeteaseApi.setCookie(store.cookie());
				status = "登录失败：" + a.error();
			}
		}));
	}

	private void loadMyPlaylists() {
		if (!isLoggedIn()) return;
		status = "正在拉取你的歌单…";
		NeteaseApi.myPlaylists(store.uid()).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			if (r.ok()) {
				myPlaylists.clear();
				myPlaylists.addAll(r.songs());
				status = "拉取到 " + r.songs().size() + " 个歌单，点击 ▸ 打开";
			} else {
				status = "拉取失败：" + r.error();
			}
			init();
		}));
	}

	private void refreshTypeButtons() {
		SearchType[] types = SearchType.values();
		for (int i = 0; i < typeButtons.size() && i < types.length; i++) {
			SearchType st = types[i];
			typeButtons.get(i).setMessage(Component.literal((st == searchType ? "● " : "") + st.label));
		}
	}

	// ---------- 弹窗（歌单选择 / 新建命名） ----------

	private int popupHeight() {
		int rows = Math.min(store.localPlaylists().size(), POPUP_MAX_ROWS);
		return 100 + rows * 24 + 30;
	}

	private int popupTop() {
		return Math.max(80, (height - popupHeight()) / 2);
	}

	private void buildPopup() {
		int cx = width / 2;
		int px = cx - POPUP_W / 2;
		int py = popupTop();
		if (namingPlaylist) {
			int bw = POPUP_W - 60;
			nameBox = new EditBox(font, px + 30, py + 42, bw, 20, Component.literal("歌单名"));
			nameBox.setMaxLength(32);
			nameBox.setValue(pendingName);
			nameBox.setHint(Component.literal("输入歌单名称"));
			addRenderableWidget(nameBox);
			setInitialFocus(nameBox);
			addRenderableWidget(Button.builder(Component.literal("确认创建"), b -> confirmCreate())
					.bounds(px + 30, py + 70, (bw - 8) / 2, 18).build());
			addRenderableWidget(Button.builder(Component.literal("取消"), b -> {
						namingPlaylist = false;
						if (pendingSong != null) pickingPlaylist = true;
						init();
					})
					.bounds(px + 42 + (bw - 8) / 2, py + 70, (bw - 8) / 2, 18).build());
			return;
		}
		int y = py + 30;
		int shown = 0;
		for (PlaylistStore.LocalPlaylist pl : store.localPlaylists()) {
			if (shown >= POPUP_MAX_ROWS) break;
			final String pid = pl.id;
			final String pname = pl.name;
			addRenderableWidget(Button.builder(Component.literal("▸ " + pname + "（" + pl.songs.size() + " 首）"),
					b -> {
						store.addToPlaylist(pid, pendingSong);
						status = "已收藏到 “" + pname + "”：" + pendingSong.name;
						pickingPlaylist = false;
						pendingSong = null;
						init();
					})
					.bounds(px + 30, y, POPUP_W - 60, 20).build());
			y += 24;
			shown++;
		}
		addRenderableWidget(Button.builder(Component.literal("＋ 新建歌单…"), b -> {
					namingPlaylist = true;
					pendingName = "";
					init();
				})
				.bounds(px + 30, y, POPUP_W - 60, 20).build());
	}

	private void confirmCreate() {
		String name = nameBox != null ? nameBox.getValue().trim() : pendingName.trim();
		if (name.isEmpty()) {
			status = "请输入歌单名称";
			return;
		}
		PlaylistStore.LocalPlaylist pl = store.createPlaylist(name);
		if (pendingSong != null) {
			store.addToPlaylist(pl.id, pendingSong);
			status = "已新建歌单并收藏：“" + pl.name + "”";
		} else {
			status = "已新建歌单：“" + pl.name + "”";
		}
		pickingPlaylist = false;
		namingPlaylist = false;
		pendingSong = null;
		if (tab == Tab.LOCAL) currentPlaylistId = pl.id;
		init();
	}

	// ---------- 输入（1.21.1 旧签名） ----------

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// ESC：二级菜单返回，一级关闭
		if (keyCode == GLFW_KEY_ESCAPE) {
			if (pickingPlaylist) {
				pickingPlaylist = false;
				pendingSong = null;
				init();
				return true;
			}
			if (namingPlaylist) {
				namingPlaylist = false;
				if (pendingSong != null) pickingPlaylist = true;
				init();
				return true;
			}
			if (tab == Tab.LOCAL && currentPlaylistId != null) {
				currentPlaylistId = null;
				init();
				return true;
			}
			if (tab == Tab.SEARCH && drilling) {
				backToSearch();
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers); // 一级：默认关闭界面
		}
		boolean inputFocused = (inputBox != null && inputBox.isFocused())
				|| (cookieBox != null && cookieBox.isFocused())
				|| (nameBox != null && nameBox.isFocused());
		if ((keyCode == GLFW_KEY_ENTER || keyCode == GLFW_KEY_KP_ENTER) && cookieBox != null && cookieBox.isFocused()) {
			doLogin(cookieBox.getValue().trim());
			return true;
		}
		if ((keyCode == GLFW_KEY_ENTER || keyCode == GLFW_KEY_KP_ENTER) && nameBox != null && nameBox.isFocused()) {
			confirmCreate();
			return true;
		}
		if ((keyCode == GLFW_KEY_ENTER || keyCode == GLFW_KEY_KP_ENTER)
				&& inputBox != null && inputBox.isFocused()) {
			if (tab == Tab.SEARCH) {
				doSearch();
				return true;
			}
			if (tab == Tab.PLAYLIST) {
				doSync();
				return true;
			}
		}
		if (!inputFocused && !pickingPlaylist && !namingPlaylist && list != null && list.hasSongs()) {
			if (keyCode == GLFW_KEY_UP) { list.moveUp(); return true; }
			if (keyCode == GLFW_KEY_DOWN) { list.moveDown(); return true; }
			if ((keyCode == GLFW_KEY_ENTER || keyCode == GLFW_KEY_KP_ENTER) && list.hasSelection()) {
				list.activateSelected();
				return true;
			}
			if ((keyCode == GLFW_KEY_DELETE || keyCode == GLFW_KEY_BACKSPACE) && list.hasSelection()) {
				if (tab == Tab.LOCAL && currentPlaylistId != null) {
					removeSelectedFromLocal();
					return true;
				}
				if (tab == Tab.LOCAL && currentPlaylistId == null) {
					int idx = (int) list.selectedSong().id;
					List<PlaylistStore.LocalPlaylist> pls = store.localPlaylists();
					if (idx >= 0 && idx < pls.size() && !"like".equals(pls.get(idx).id)) {
						store.removePlaylist(pls.get(idx).id);
						status = "已删除歌单：" + pls.get(idx).name;
						init();
						return true;
					}
				}
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (list != null
				&& mouseX >= list.x && mouseX < list.x + list.w
				&& mouseY >= list.y && mouseY < list.y + list.h) {
			list.mouseScrolled(verticalAmount);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (list != null && list.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	// ---------- 渲染（1.21.1：render + GuiGraphics） ----------

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		renderBackground(g, mouseX, mouseY, partialTick);
		super.render(g, mouseX, mouseY, partialTick);

		g.fill(0, 0, width, height, 0xC0101010);
		g.drawCenteredString(font, "AllMusic 点歌", width / 2, 2, 0xFFFFFFFF);
		int cx = width / 2;
		int lx = cx - CONTENT_W / 2;

		if (tab == Tab.MINE) {
			renderMine(g, lx, mouseX, mouseY);
		} else {
			g.drawString(font, listTitle, lx, 54, 0xFFA0A0A0);
			if (list != null) list.render(g, mouseX, mouseY);
			if (tab == Tab.SEARCH && !hasSearched && !drilling) {
				g.drawCenteredString(font, "请输入关键词并搜索歌曲", width / 2, height / 2 - 4, 0xFF8F8F8F);
				g.drawCenteredString(font, "支持单曲 / 歌手 / 专辑 / 歌单", width / 2, height / 2 + 8, 0xFF6E6E6E);
			}
			if (tab == Tab.LOCAL && currentPlaylistId == null && store.localPlaylists().isEmpty()) {
				g.drawCenteredString(font, "还没有本地歌单，点「新建歌单」创建", width / 2, height / 2 - 4, 0xFF8F8F8F);
			}
		}
		g.drawCenteredString(font, status, width / 2, height - TAB_H - 18, 0xFF8F8F8F);

		if (pickingPlaylist || namingPlaylist) {
			drawPopup(g, mouseX, mouseY);
		}
	}

	private void drawPopup(GuiGraphics g, int mx, int my) {
		int cx = width / 2;
		int px = cx - POPUP_W / 2;
		int ph = popupHeight();
		int py = popupTop();
		g.fill(px, py, px + POPUP_W, py + ph, 0xF0202020);
		g.fill(px, py, px + POPUP_W, py + 1, 0xFF555555);
		g.fill(px, py + ph - 1, px + POPUP_W, py + ph, 0xFF555555);
		g.fill(px, py, px + 1, py + ph, 0xFF555555);
		g.fill(px + POPUP_W - 1, py, px + POPUP_W, py + ph, 0xFF555555);
		g.drawCenteredString(font, namingPlaylist ? "新建歌单" : "选择歌单（收藏到）", cx, py + 8, 0xFFFFFFFF);
		if (namingPlaylist && pendingSong != null) {
			g.drawString(font, "将收藏：" + pendingSong.name, px + 30, py + 24, 0xFFC0C0C0);
		}
		if (pickingPlaylist && pendingSong != null) {
			g.drawString(font, pendingSong.name, px + 30, py + 20, 0xFFC0C0C0);
		}
		g.drawString(font, "ESC 取消", px + POPUP_W - 60, py + ph - 14, 0xFF6E6E6E);
	}

	private void renderMine(GuiGraphics g, int lx, int mx, int my) {
		int top = mineTop();
		int bottom = mineBottom();

		int ax = lx + 4;
		int aw = 48;
		g.fill(ax, top, ax + aw, top + aw, 0xFF202020);
		g.fill(ax, top, ax + aw, top + 1, 0xFF555555);
		g.fill(ax, top + aw - 1, ax + aw, top + aw, 0xFF555555);
		g.fill(ax, top, ax + 1, top + aw, 0xFF555555);
		g.fill(ax + aw - 1, top, ax + aw, top + aw, 0xFF555555);
		g.fill(ax + 18, top + 10, ax + 30, top + 22, 0xFF8A8A8A);
		g.fill(ax + 12, top + 28, ax + 36, top + 40, 0xFF8A8A8A);
		String name = isLoggedIn() ? store.nickname() : (loginMode ? "登录中…" : "未登录");
		g.drawString(font, isLoggedIn() ? "已登录" : "未登录", ax + 56, top + 2, isLoggedIn() ? 0xFF55FF55 : 0xFF8F8F8F);
		g.drawString(font, name, ax + 56, top + 14, 0xFFA0A0A0);

		if (list != null) list.render(g, mx, my);
		PlaylistStore.LocalPlaylist like = store.byId("like");
		int likeCount = like != null ? like.songs.size() : 0;
		g.drawString(font, "本地歌单 · 喜欢（" + likeCount + "）", lx + 4, mineTop() + 52, 0xFFA0A0A0);

		int rx = lx + LEFT_COL_W + 10;
		int rw = CONTENT_W - LEFT_COL_W - 10 - 8;
		g.fill(rx, top, rx + rw, bottom, 0xFF2B2B2B);
		g.fill(rx, top, rx + rw, top + 1, 0xFF555555);
		g.fill(rx, bottom - 1, rx + rw, bottom, 0xFF555555);
		g.fill(rx, top, rx + 1, bottom, 0xFF555555);
		g.fill(rx + rw - 1, top, rx + rw, bottom, 0xFF555555);
		g.drawCenteredString(font, "设置页", rx + rw / 2, top + 6, 0xFFFFFFFF);

		int y = top + 24;
		g.drawString(font, "账号", rx + 8, y, 0xFFA0A0A0);
		y += 14;
		if (isLoggedIn()) {
			g.drawString(font, store.nickname() + "（uid " + store.uid() + "）", rx + 12, y, 0xFFC0C0C0);
		} else if (!loginMode) {
			g.drawString(font, "未登录：点「点击登录」粘贴 MUSIC_U", rx + 12, y, 0xFF6E6E6E);
		}
		g.drawString(font, "我的云歌单", rx + 8, yMyCloudLabel, 0xFFA0A0A0);
		g.drawString(font, "同步歌单管理", rx + 8, ySyncedLabel, 0xFFA0A0A0);
		List<PlaylistStore.SyncedPlaylist> synced = store.syncedPlaylists();
		int sy = ySyncedListTop;
		if (synced.isEmpty()) {
			g.drawString(font, "（暂无同步歌单）", rx + 12, sy, 0xFF6E6E6E);
			sy += 14;
		}
		for (PlaylistStore.SyncedPlaylist sp : synced) {
			g.drawString(font, sp.name + "（" + sp.songs.size() + " 首）", rx + 12, sy + 2, 0xFFC0C0C0);
			sy += 22;
		}
		g.drawString(font, "配置：allmusic-gui/playlists.json", rx + 8, bottom - 14, 0xFF6E6E6E);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
