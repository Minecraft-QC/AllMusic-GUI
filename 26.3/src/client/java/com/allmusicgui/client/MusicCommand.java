package com.allmusicgui.client;

import com.allmusicgui.AllMusicGUI;
import com.allmusicgui.model.Song;

import net.minecraft.client.Minecraft;

/** 点歌命令发送：向服务器聊天发送 /music <ID>，由 AllMusic 服务端插件处理（26.3 版） */
public final class MusicCommand {
	private MusicCommand() {
	}

	public static void request(Song song) {
		if (song == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) {
			feedback("未连接服务器，无法点歌");
			return;
		}
		String cmd = "music " + song.id;
		mc.getConnection().sendCommand(cmd);
		feedback("已点歌：" + song.name + "（/music " + song.id + "）");
	}

	private static void feedback(String msg) {
		// 26.3 已移除 setOverlayMessage；界面状态栏会显示结果，这里仅记日志。
		AllMusicGUI.LOGGER.info("[AllMusicGUI] " + msg);
	}
}
