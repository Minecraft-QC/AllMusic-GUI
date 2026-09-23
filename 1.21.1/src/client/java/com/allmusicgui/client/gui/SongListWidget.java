package com.allmusicgui.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.allmusicgui.model.Song;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** 简易可滚动歌单列表（1.21.1 旧渲染：GuiGraphics）。原版风格。 */
public class SongListWidget {
	public interface ClickHandler {
		/** button: GLFW 鼠标键（0=左键 播放，1=右键 收藏） */
		void onClick(Song song, int button);
	}

	private static final int ROW_H = 26;

	private final Font font;
	private final ClickHandler onClick;
	private final List<Song> songs = new ArrayList<>();

	int x, y, w, h;
	private int scroll;
	private int selected = -1;

	public SongListWidget(Font font, ClickHandler onClick) {
		this.font = font;
		this.onClick = onClick;
	}

	public void setBounds(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	public void setSongs(List<Song> songs) {
		this.songs.clear();
		this.songs.addAll(songs);
		this.selected = -1;
		this.scroll = 0;
	}

	public Song selectedSong() {
		return (selected >= 0 && selected < songs.size()) ? songs.get(selected) : null;
	}

	public void render(GuiGraphics g, int mx, int my) {
		g.fill(x, y, x + w, y + h, 0x40101010);
		if (songs.isEmpty()) return;
		int visible = Math.max(1, h / ROW_H);
		int maxScroll = Math.max(0, songs.size() - visible);
		if (scroll > maxScroll) scroll = maxScroll;

		g.enableScissor(x, y, x + w, y + h);
		for (int i = scroll; i < Math.min(songs.size(), scroll + visible + 1); i++) {
			int ry = y + (i - scroll) * ROW_H;
			Song s = songs.get(i);
			boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
			int bg = hover ? 0x40FFFFFF : (i == selected ? 0x60FFFFFF : 0);
			if (bg != 0) g.fill(x, ry, x + w, ry + ROW_H, bg);
			g.drawString(font, (i + 1) + ". " + s.name, x + 4, ry + 3, 0xFFFFFFFF);
			g.drawString(font, s.sub, x + 4, ry + 14, 0xFF9B9B9B);
			String right = s.durationText().isEmpty() ? s.type : s.durationText() + "  " + s.type;
			g.drawString(font, right, x + w - font.width(right) - 4, ry + 7, 0xFF8A8A8A);
		}
		g.disableScissor();
	}

	public boolean mouseClicked(double mx, double my, int button) {
		if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
		int idx = (int) ((my - y) / ROW_H) + scroll;
		if (idx >= 0 && idx < songs.size()) {
			selected = idx;
			if (onClick != null) onClick.onClick(songs.get(idx), button);
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double amount) {
		if (songs.isEmpty()) return false;
		int visible = Math.max(1, h / ROW_H);
		int maxScroll = Math.max(0, songs.size() - visible);
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(amount) * 3));
		return true;
	}

	// ---------- 键盘/手柄导航（Controlify D-pad 映射方向键） ----------

	public boolean hasSongs() { return !songs.isEmpty(); }
	public boolean hasSelection() { return selected >= 0 && selected < songs.size(); }

	/** 方向键上移选中项，自动滚动。 */
	public void moveUp() {
		if (songs.isEmpty()) return;
		if (selected < 0) { selected = songs.size() - 1; }
		else if (selected > 0) { selected--; }
		ensureVisible();
	}

	/** 方向键下移选中项，自动滚动。 */
	public void moveDown() {
		if (songs.isEmpty()) return;
		if (selected < 0) { selected = 0; }
		else if (selected < songs.size() - 1) { selected++; }
		ensureVisible();
	}

	private void ensureVisible() {
		int visible = Math.max(1, h / ROW_H);
		if (selected < scroll) scroll = selected;
		else if (selected >= scroll + visible) scroll = selected - visible + 1;
		int maxScroll = Math.max(0, songs.size() - visible);
		scroll = Math.max(0, Math.min(maxScroll, scroll));
	}

	/** 触发左键（播放/打开）。 */
	public void activateSelected() {
		if (hasSelection() && onClick != null) onClick.onClick(songs.get(selected), 0);
	}

	/** 触发右键（收藏）。 */
	public void favoriteSelected() {
		if (hasSelection() && onClick != null) onClick.onClick(songs.get(selected), 1);
	}
}
