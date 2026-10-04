package top.colorgarden.mapdrawclient.input;

import top.colorgarden.mapdrawclient.compat.Compat;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import top.colorgarden.mapdrawclient.ui.MainMenuScreen;

/**
 * 客户端键位注册。
 *
 * <p>全部走原版 {@link KeyMapping}，所以都能在「选项 → 控制 → 按键绑定 → MapDraw Client」
 * 里看到并改键（之前画板里的快捷键是硬编码 GLFW 键码，改不了）。</p>
 *
 * <p>只有 {@code open_menu} 是全局生效的；其余都是<b>界面内快捷键</b>：
 * 画板打开时由 {@code BoardScreen} 用 {@link #actionFor(KeyEvent)} 匹配，
 * 界面没开时它们什么都不做。</p>
 */
public final class MapDrawKeys {
	private MapDrawKeys() {
	}

	/** 自定义分类（在按键绑定界面里单独一组）。26.2 起是 KeyMapping.Category，旧版是字符串。 */
	//#if MC >= 12111
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("mapdrawclient", "main"));
	//#else
	//$$ public static final String CATEGORY = "key.categories.mapdrawclient";
	//#endif

	// ---- 全局 ----
	public static KeyMapping openMenu;

	// ---- 画板内 ----
	public static KeyMapping toolPen;
	public static KeyMapping toolEraser;
	public static KeyMapping toolBucket;
	public static KeyMapping toolNone;
	public static KeyMapping brushDown;
	public static KeyMapping brushUp;
	public static KeyMapping undo;
	public static KeyMapping redo;
	public static KeyMapping sync;
	public static KeyMapping protect;
	public static KeyMapping grid;
	public static KeyMapping resetPan;
	public static KeyMapping canvasList;
	public static KeyMapping serverMenu;
	public static KeyMapping readHeld;
	public static KeyMapping zoomIn;
	public static KeyMapping zoomOut;
	public static KeyMapping backMenu;

	/** 画板内动作。 */
	public enum Action {
		TOOL_PEN,
		TOOL_ERASER,
		TOOL_BUCKET,
		TOOL_NONE,
		BRUSH_DOWN,
		BRUSH_UP,
		UNDO,
		REDO,
		SYNC,
		PROTECT,
		GRID,
		RESET_PAN,
		CANVAS_LIST,
		SERVER_MENU,
		READ_HELD,
		ZOOM_IN,
		ZOOM_OUT,
		BACK_MENU,
		NONE
	}

	private static final List<KeyMapping> ALL = new ArrayList<>();

	public static void init() {
		openMenu = register("open_menu", GLFW.GLFW_KEY_J);
		toolPen = register("tool_pen", GLFW.GLFW_KEY_1);
		toolEraser = register("tool_eraser", GLFW.GLFW_KEY_2);
		toolBucket = register("tool_bucket", GLFW.GLFW_KEY_3);
		toolNone = register("tool_none", GLFW.GLFW_KEY_4);
		brushDown = register("brush_down", GLFW.GLFW_KEY_COMMA);
		brushUp = register("brush_up", GLFW.GLFW_KEY_PERIOD);
		undo = register("undo", GLFW.GLFW_KEY_Z);
		redo = register("redo", GLFW.GLFW_KEY_Y);
		sync = register("sync", GLFW.GLFW_KEY_S);
		protect = register("protect", GLFW.GLFW_KEY_K);
		grid = register("grid", GLFW.GLFW_KEY_G);
		resetPan = register("reset_pan", GLFW.GLFW_KEY_R);
		canvasList = register("canvas_list", GLFW.GLFW_KEY_L);
		serverMenu = register("server_menu", GLFW.GLFW_KEY_O);
		readHeld = register("read_held", GLFW.GLFW_KEY_H);
		zoomIn = register("zoom_in", GLFW.GLFW_KEY_EQUAL);
		zoomOut = register("zoom_out", GLFW.GLFW_KEY_MINUS);
		backMenu = register("back_menu", GLFW.GLFW_KEY_BACKSPACE);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenu.consumeClick()) {
				openMenu(client);
			}

			// 界面内快捷键
			for (KeyMapping mapping : ALL) {
				while (mapping.consumeClick()) {
					//#if MC >= 12111
					// 26.2+：真正的处理在 MapDrawScreen.onKeyEvent 里按事件匹配，这里只清队列
					//#else
					//$$ // 旧版本没有事件对象：直接在这里派发给打开着的画板
					//$$ net.minecraft.client.gui.screens.Screen screen =
					//$$ 		top.colorgarden.mapdrawclient.compat.Compat.screen(client);
					//$$ if (screen instanceof top.colorgarden.mapdrawclient.ui.BoardScreen board) {
					//$$ 	board.onKeybindTriggered(actionFor(mapping));
					//$$ }
					//#endif
				}
			}
		});
	}

	/** 由 KeyMapping 反查动作（旧版本的派发路径用）。 */
	public static Action actionFor(KeyMapping mapping) {
		if (mapping == toolPen) {
			return Action.TOOL_PEN;
		}

		if (mapping == toolEraser) {
			return Action.TOOL_ERASER;
		}

		if (mapping == toolBucket) {
			return Action.TOOL_BUCKET;
		}

		if (mapping == toolNone) {
			return Action.TOOL_NONE;
		}

		if (mapping == brushDown) {
			return Action.BRUSH_DOWN;
		}

		if (mapping == brushUp) {
			return Action.BRUSH_UP;
		}

		if (mapping == undo) {
			return Action.UNDO;
		}

		if (mapping == redo) {
			return Action.REDO;
		}

		if (mapping == sync) {
			return Action.SYNC;
		}

		if (mapping == protect) {
			return Action.PROTECT;
		}

		if (mapping == grid) {
			return Action.GRID;
		}

		if (mapping == resetPan) {
			return Action.RESET_PAN;
		}

		if (mapping == canvasList) {
			return Action.CANVAS_LIST;
		}

		if (mapping == serverMenu) {
			return Action.SERVER_MENU;
		}

		if (mapping == readHeld) {
			return Action.READ_HELD;
		}

		if (mapping == zoomIn) {
			return Action.ZOOM_IN;
		}

		if (mapping == zoomOut) {
			return Action.ZOOM_OUT;
		}

		if (mapping == backMenu) {
			return Action.BACK_MENU;
		}

		return Action.NONE;
	}

	private static KeyMapping register(String name, int defaultKey) {
		KeyMapping mapping = Compat.registerKeyMapping(new KeyMapping(
				"key.mapdrawclient." + name,
				InputConstants.Type.KEYSYM,
				defaultKey,
				CATEGORY));
		ALL.add(mapping);
		return mapping;
	}

	/** 某个按键事件命中的画板动作（没命中返回 {@link Action#NONE}）。 */
	public static Action actionFor(KeyEvent event) {
		if (toolPen.matches(event)) {
			return Action.TOOL_PEN;
		}

		if (toolEraser.matches(event)) {
			return Action.TOOL_ERASER;
		}

		if (toolBucket.matches(event)) {
			return Action.TOOL_BUCKET;
		}

		if (toolNone.matches(event)) {
			return Action.TOOL_NONE;
		}

		if (brushDown.matches(event)) {
			return Action.BRUSH_DOWN;
		}

		if (brushUp.matches(event)) {
			return Action.BRUSH_UP;
		}

		if (undo.matches(event)) {
			return Action.UNDO;
		}

		if (redo.matches(event)) {
			return Action.REDO;
		}

		if (sync.matches(event)) {
			return Action.SYNC;
		}

		if (protect.matches(event)) {
			return Action.PROTECT;
		}

		if (grid.matches(event)) {
			return Action.GRID;
		}

		if (resetPan.matches(event)) {
			return Action.RESET_PAN;
		}

		if (canvasList.matches(event)) {
			return Action.CANVAS_LIST;
		}

		if (serverMenu.matches(event)) {
			return Action.SERVER_MENU;
		}

		if (readHeld.matches(event)) {
			return Action.READ_HELD;
		}

		if (zoomIn.matches(event)) {
			return Action.ZOOM_IN;
		}

		if (zoomOut.matches(event)) {
			return Action.ZOOM_OUT;
		}

		if (backMenu.matches(event)) {
			return Action.BACK_MENU;
		}

		return Action.NONE;
	}

	private static void openMenu(Minecraft client) {
		if (client.player == null) {
			return;
		}

		top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new MainMenuScreen(null));
	}
}
