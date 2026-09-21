package com.allmusicgui.client;

import com.allmusicgui.AllMusicGUI;
import com.allmusicgui.client.gui.AllMusicGuiScreen;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;

/**
 * 客户端入口：注册可配置按键打开点歌界面（26.3）。
 * 默认 G，可在 选项 → 控制 中修改。
 */
public class AllMusicGUIClient implements ClientModInitializer {
	private static KeyMapping openKey;
	private static boolean ourScreenOpen = false;

	public static void markClosed() {
		ourScreenOpen = false;
	}

	@Override
	public void onInitializeClient() {
		openKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.allmusic-gui.open", InputConstants.KEY_G, KeyMapping.Category.MISC)
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (!ourScreenOpen && client.player != null) {
					ourScreenOpen = true;
					client.setScreenAndShow(new AllMusicGuiScreen());
				}
			}
		});

		AllMusicGUI.LOGGER.info("[AllMusicGUI] 客户端初始化完成，按键默认 G（可在控制设置中修改）");
	}
}
