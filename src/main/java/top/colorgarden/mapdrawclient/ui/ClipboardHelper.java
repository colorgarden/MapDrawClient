package top.colorgarden.mapdrawclient.ui;

import net.minecraft.client.Minecraft;

/**
 * 剪贴板读取 (Ctrl+V 粘贴画布 UUID 用)。
 *
 * <p>只用到 {@code Minecraft#keyboardHandler}，若 26.2 有变动，
 * 直接删除本文件与 {@link MapDrawScreen} 中调用它的那几行即可，不影响其它功能。</p>
 */
public final class ClipboardHelper {
	private ClipboardHelper() {
	}

	public static String get() {
		try {
			Minecraft client = Minecraft.getInstance();
			String text = client.keyboardHandler.getClipboard();
			return text == null ? "" : text;
		} catch (Throwable t) {
			return "";
		}
	}
}
