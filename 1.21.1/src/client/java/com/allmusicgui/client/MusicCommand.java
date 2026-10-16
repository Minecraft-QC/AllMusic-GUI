package com.allmusicgui.client;

import com.allmusicgui.model.Song;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 点歌命令发送：向服务器聊天发送 /music <ID>，由 AllMusic 服务端插件处理 */
public final class MusicCommand {
	private MusicCommand() {
	}

	public static void request(Song song) {
		if (song == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) {
			overlay(mc, "§c未连接服务器，无法点歌");
			return;
		}
		String cmd = "music " + song.id;
		mc.getConnection().sendCommand(cmd);
		overlay(mc, "§a已点歌 §f" + song.name + " §7(/music " + song.id + ")");
	}

	/** 发送任意 AllMusic 子指令（vote / stop / hud lyric enable 等，均为普通玩家可用）。 */
	public static void run(String args, String label) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) {
			overlay(mc, "§c未连接服务器，无法发送指令");
			return;
		}
		mc.getConnection().sendCommand("music " + args);
		overlay(mc, "§a" + label + " §7(/music " + args + ")");
	}

	private static void overlay(Minecraft mc, String msg) {
		if (mc.gui != null) {
			mc.gui.setOverlayMessage(Component.literal(msg), false);
		}
	}
}
