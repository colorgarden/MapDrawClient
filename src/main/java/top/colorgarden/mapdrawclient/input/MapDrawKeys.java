package top.colorgarden.mapdrawclient.input;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import top.colorgarden.mapdrawclient.ui.MainMenuScreen;

/**
 * 客户端键位：**只保留一个** —— 按 J 打开控制台菜单，其余界面都从菜单里进。
 *
 * <p>用 J 而不是 M：M 常被小地图/世界地图类模组占用。</p>
 *
 * <p>另外在游戏里也可以「手持画布地图 → 右键」直接开画板（见 {@link BoardOpenHandler}）。</p>
 */
public final class MapDrawKeys {
	private MapDrawKeys() {
	}

	/** 唯一键位：打开控制台菜单。 */
	public static KeyMapping openMenu;

	public static void init() {
		openMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.mapdrawclient.open_menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_J,
				KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenu.consumeClick()) {
				openMenu(client);
			}
		});
	}

	private static void openMenu(Minecraft client) {
		if (client.player == null) {
			return;
		}

		client.gui.setScreen(new MainMenuScreen(null));
	}
}
