package com.allmusicgui;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllMusicGUI implements ModInitializer {
	public static final String MOD_ID = "allmusic-gui";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("[AllMusicGUI] 客户端点歌界面已加载，按 G 打开");
	}
}
