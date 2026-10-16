package com.allmusicgui.model;

/**
 * 歌单条目模型。
 * 单曲 / 歌手 / 专辑 / 歌单 统一用一个结构表示，
 * type 字段区分来源类型，sub 为副标题（歌手-专辑 / 歌单简介等）。
 */
public class Song {
	/** 类型常量：与 SearchType 的 label 保持一致，避免界面层散落中文魔串。 */
	public static final String TYPE_SONG = "单曲";
	public static final String TYPE_ARTIST = "歌手";
	public static final String TYPE_ALBUM = "专辑";
	public static final String TYPE_PLAYLIST = "歌单";

	public long id;
	public String name = "";
	public String sub = "";
	public String type = TYPE_SONG;
	public int duration; // 秒，单曲可用

	public Song() {
	}

	public Song(long id, String name, String sub, String type, int duration) {
		this.id = id;
		this.name = name;
		this.sub = sub;
		this.type = type;
		this.duration = duration;
	}

	public String durationText() {
		if (duration <= 0) return "";
		return String.format("%d:%02d", duration / 60, duration % 60);
	}

	@Override
	public String toString() {
		return type + ":" + name;
	}
}
