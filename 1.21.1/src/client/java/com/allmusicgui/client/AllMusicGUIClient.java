package com.allmusicgui.client;

import com.allmusicgui.AllMusicGUI;
import com.allmusicgui.client.gui.AllMusicGuiScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：注册可配置按键打开点歌界面（1.21.1）。
 * 默认 G，可在 选项 → 控制 中修改。
 */
public class AllMusicGUIClient implements ClientModInitializer {
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		openKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.allmusic-gui.open", GLFW.GLFW_KEY_G, "key.categories.misc")
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new AllMusicGuiScreen());
				}
			}
		});

		AllMusicGUI.LOGGER.info("[AllMusicGUI] 客户端初始化完成，按键默认 G（可在控制设置中修改）");
	}
}
