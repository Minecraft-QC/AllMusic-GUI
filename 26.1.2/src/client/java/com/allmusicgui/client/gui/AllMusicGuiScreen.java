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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * AllMusic 点歌界面（Minecraft 原版风格，26.x 渲染）。
 * 顶部输入框；中间内容；底部四页签：搜索 / 歌单(本地) / 云歌单(同步) / 我的。
 * 搜索歌手/专辑/歌单后点击可下钻到歌曲列表（二级菜单），左键点歌、右键收藏。
 * 26.x 注意：自绘颜色必须带非零 alpha，否则 fill/text 被丢弃。
 */
public class AllMusicGuiScreen extends Screen {
	public enum Tab { SEARCH, LOCAL, PLAYLIST, MINE }

	private static final int CONTENT_W = 460;
	private static final int TAB_H = 24;
	private static final int GLFW_KEY_ENTER = 257;
	private static final int GLFW_KEY_KP_ENTER = 335;
	private static final int LEFT_COL_W = 250;

	private final PlaylistStore store = new PlaylistStore();
	private Tab tab = Tab.SEARCH;
	private String status = "左键点歌 / 右键收藏到本地歌单（需服务器安装 AllMusic 插件）";

	private String keyword = "";
	private String playlistId = "";
	private SearchType searchType = SearchType.SONG;

	private boolean closeAfterPlay = true;
	private boolean overwriteSync = true;
	private boolean loginMode = false;
	private final List<Song> myPlaylists = new ArrayList<>();
	private boolean autoTried = false;

	// 右栏标签 y 坐标：buildMinePage 计算、renderMine 绘制
	private int yMyCloudLabel;
	private int ySyncedLabel;
	private int ySyncedListTop;

	/** 搜索结果（歌手/专辑/歌单/单曲），切页签后保留 */
	private final List<Song> searchResults = new ArrayList<>();
	/** 下钻后的歌曲列表（点歌手/专辑/歌单进入） */
	private final List<Song> drillSongs = new ArrayList<>();
	private boolean drilling = false;
	private boolean hasSearched = false;

	private final List<Song> displaySongs = new ArrayList<>();
	private String listTitle = "搜索结果";
	private Song lastClicked;

	private EditBox inputBox;
	private EditBox cookieBox;
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

		addRenderableOnly((g, mx, my, delta) -> drawContent(g, mx, my));

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
				setInitialFocus(inputBox);
				typeButtons.clear();
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
				if (drilling) {
					addRenderableWidget(Button.builder(Component.literal("← 返回"), b -> backToSearch())
							.bounds(lx + CONTENT_W - 60, 38, 60, 18).build());
				}
				listTitle = drilling ? "结果" : "搜索结果";
			}
			case LOCAL -> {
				addRenderableWidget(Button.builder(Component.literal("移除选中"), b -> removeSelected())
						.bounds(lx + CONTENT_W - 190, 38, 90, 18).build());
				addRenderableWidget(Button.builder(Component.literal("清空"), b -> {
							store.clearLocal();
							displaySongs.clear();
							displaySongs.addAll(store.localSongs());
							if (list != null) list.setSongs(displaySongs);
							listTitle = "本地歌单（0 首）";
							status = "本地歌单已清空";
						})
						.bounds(lx + CONTENT_W - 94, 38, 90, 18).build());
				listTitle = "本地歌单（" + store.localSongs().size() + " 首）";
			}
			case PLAYLIST -> {
				inputBox.setValue(playlistId);
				inputBox.setHint(Component.literal("输入网易云歌单 ID（数字）"));
				setInitialFocus(inputBox);
				addRenderableWidget(Button.builder(Component.literal("同步"), b -> doSync())
						.bounds(lx + CONTENT_W - 90, 38, 86, 18).build());
				listTitle = "云歌单（输入 ID 同步）";
			}
			case MINE -> {
				buildMinePage(lx);
				return;
			}
		}

		list = new SongListWidget(font, this::onSongClick);
		list.setBounds(lx, 64, CONTENT_W, Math.max(80, height - 64 - TAB_H - 38));
		displaySongs.clear();
		switch (tab) {
			case SEARCH -> displaySongs.addAll(drilling ? drillSongs : searchResults);
			case LOCAL -> displaySongs.addAll(store.localSongs());
			case PLAYLIST -> { /* 由 openPlaylist/doSync 填充 */ }
			default -> {}
		}
		list.setSongs(displaySongs);

		addTabButtons(lx);
	}

	private int mineTop() { return 64; }
	private int mineBottom() { return height - TAB_H - 38; }

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
		displaySongs.addAll(store.localSongs());
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
		tab = t;
		init();
	}

	private void onSongClick(Song s, int button) {
		lastClicked = s;
		if (button == 1) {
			if ("单曲".equals(s.type)) {
				store.addLocal(s);
				status = "已收藏：" + s.name;
				if (tab == Tab.LOCAL) init();
			} else {
				status = "仅单曲可右键收藏：" + s.name;
			}
			return;
		}
		// 左键
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

	private void removeSelected() {
		Song s = list != null ? list.selectedSong() : null;
		if (s == null) {
			status = "请先选择要移除的歌曲";
			return;
		}
		store.removeLocal(s);
		displaySongs.clear();
		displaySongs.addAll(store.localSongs());
		if (list != null) list.setSongs(displaySongs);
		listTitle = "本地歌单（" + store.localSongs().size() + " 首）";
		status = "已移除：" + s.name;
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
		NeteaseApi.search(keyword, searchType, 30).thenAccept(r -> Minecraft.getInstance().execute(() -> {
			if (r.ok()) {
				searchResults.clear();
				searchResults.addAll(r.songs());
				displaySongs.clear();
				displaySongs.addAll(searchResults);
				if (list != null) list.setSongs(displaySongs);
				listTitle = "搜索结果";
				status = r.songs().isEmpty() ? "没有找到结果" : "找到 " + r.songs().size() + " 条（左键打开/点歌）";
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

	// ---------- 输入 ----------

	@Override
	public boolean keyPressed(KeyEvent e) {
		if ((e.key() == GLFW_KEY_ENTER || e.key() == GLFW_KEY_KP_ENTER) && cookieBox != null && cookieBox.isFocused()) {
			doLogin(cookieBox.getValue().trim());
			return true;
		}
		if ((e.key() == GLFW_KEY_ENTER || e.key() == GLFW_KEY_KP_ENTER)
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
		return super.keyPressed(e);
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
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (list != null && list.mouseClicked(event.x(), event.y(), event.button())) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	// ---------- 自绘内容 ----------

	private void drawContent(GuiGraphicsExtractor g, int mx, int my) {
		g.fill(0, 0, width, height, 0xC0101010);
		g.centeredText(font, "AllMusic 点歌", width / 2, 2, 0xFFFFFFFF);
		int cx = width / 2;
		int lx = cx - CONTENT_W / 2;

		if (tab == Tab.MINE) {
			renderMine(g, lx, mx, my);
		} else {
			g.text(font, listTitle, lx, 54, 0xFFA0A0A0);
			if (list != null) list.render(g, mx, my);
			// 未搜索时的占位提示
			if (tab == Tab.SEARCH && !hasSearched && !drilling) {
				g.centeredText(font, "请输入关键词并搜索歌曲", width / 2, height / 2 - 4, 0xFF8F8F8F);
				g.centeredText(font, "支持单曲 / 歌手 / 专辑 / 歌单", width / 2, height / 2 + 8, 0xFF6E6E6E);
			}
		}
		g.centeredText(font, status, width / 2, height - TAB_H - 18, 0xFF8F8F8F);
	}

	private void renderMine(GuiGraphicsExtractor g, int lx, int mx, int my) {
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
		g.text(font, isLoggedIn() ? "已登录" : "未登录", ax + 56, top + 2, isLoggedIn() ? 0xFF55FF55 : 0xFF8F8F8F);
		g.text(font, name, ax + 56, top + 14, 0xFFA0A0A0);

		if (list != null) list.render(g, mx, my);

		int rx = lx + LEFT_COL_W + 10;
		int rw = CONTENT_W - LEFT_COL_W - 10 - 8;
		g.fill(rx, top, rx + rw, bottom, 0xFF2B2B2B);
		g.fill(rx, top, rx + rw, top + 1, 0xFF555555);
		g.fill(rx, bottom - 1, rx + rw, bottom, 0xFF555555);
		g.fill(rx, top, rx + 1, bottom, 0xFF555555);
		g.fill(rx + rw - 1, top, rx + rw, bottom, 0xFF555555);
		g.centeredText(font, "设置页", rx + rw / 2, top + 6, 0xFFFFFFFF);

		int y = top + 24;
		g.text(font, "账号", rx + 8, y, 0xFFA0A0A0);
		y += 14;
		if (isLoggedIn()) {
			g.text(font, store.nickname() + "（uid " + store.uid() + "）", rx + 12, y, 0xFFC0C0C0);
		} else if (!loginMode) {
			g.text(font, "未登录：点「点击登录」粘贴 MUSIC_U", rx + 12, y, 0xFF6E6E6E);
		}
		g.text(font, "我的云歌单", rx + 8, yMyCloudLabel, 0xFFA0A0A0);
		g.text(font, "同步歌单管理", rx + 8, ySyncedLabel, 0xFFA0A0A0);
		List<PlaylistStore.SyncedPlaylist> synced = store.syncedPlaylists();
		int sy = ySyncedListTop;
		if (synced.isEmpty()) {
			g.text(font, "（暂无同步歌单）", rx + 12, sy, 0xFF6E6E6E);
			sy += 14;
		}
		for (PlaylistStore.SyncedPlaylist sp : synced) {
			g.text(font, sp.name + "（" + sp.songs.size() + " 首）", rx + 12, sy + 2, 0xFFC0C0C0);
			sy += 22;
		}
		g.text(font, "配置：allmusic-gui/playlists.json", rx + 8, bottom - 14, 0xFF6E6E6E);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
