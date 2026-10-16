package com.allmusicgui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 常量与日志持有类。
 * 注意：本 mod 为纯客户端（fabric.mod.json 仅注册 client 入口），
 * 未实现 ModInitializer，避免误导别人以为有主入口初始化逻辑。
 */
public final class AllMusicGUI {
	private AllMusicGUI() {
	}

	public static final String MOD_ID = "allmusic-gui";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
}
