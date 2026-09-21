package com.allmusicgui.model;

/**
 * 歌单条目模型。
 * 单曲 / 歌手 / 专辑 / 歌单 统一用一个结构表示，
 * type 字段区分来源类型，sub 为副标题（歌手-专辑 / 歌单简介等）。
 */
public class Song {
	public long id;
	public String name = "";
	public String sub = "";
	public String type = "单曲";
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
