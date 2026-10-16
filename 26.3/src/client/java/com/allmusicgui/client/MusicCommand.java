package com.allmusicgui.client;

import com.allmusicgui.AllMusicGUI;
import com.allmusicgui.model.Song;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

	/** 发送任意 AllMusic 子指令（vote / stop / hud lyric enable 等，均为普通玩家可用）。 */
	public static void run(String args, String label) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) {
			feedback("未连接服务器，无法发送指令");
			return;
		}
		mc.getConnection().sendCommand("music " + args);
		feedback(label + "（/music " + args + "）");
	}

	private static void feedback(String msg) {
		// 26.3 已移除 setOverlayMessage，改用对应的 sendOverlayMessage，玩家在游戏内能看到点歌结果
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(msg));
		}
		AllMusicGUI.LOGGER.info("[AllMusicGUI] " + msg);
	}
}
