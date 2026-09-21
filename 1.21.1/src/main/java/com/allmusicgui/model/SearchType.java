package com.allmusicgui.model;

/** 网易云搜索类型：单曲 / 歌手 / 专辑 / 歌单 */
public enum SearchType {
	SONG(1, "单曲"),
	ARTIST(100, "歌手"),
	ALBUM(10, "专辑"),
	PLAYLIST(1000, "歌单");

	public final int neteaseType;
	public final String label;

	SearchType(int neteaseType, String label) {
		this.neteaseType = neteaseType;
		this.label = label;
	}
}
