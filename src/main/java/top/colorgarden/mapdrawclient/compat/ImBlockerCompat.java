package top.colorgarden.mapdrawclient.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.fabricmc.loader.api.FabricLoader;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * IMBlocker 兼容：让自绘输入框也能用输入法（中文）。
 *
 * <p>IMBlocker 只认它注入过的文本框组件（原版 {@code EditBox} 等），
 * 我们的输入框是自己画的，所以它会一直把输入法关掉。IMBlocker 5.1+ 提供了
 * 焦点管理 API：实现 {@code io.github.reserveword.imblocker.common.gui.FocusableObject}
 * 并在获得焦点时交给 {@code FocusManager.setFocusOwner(...)}，它就会按我们的
 * 意愿打开输入法。</p>
 *
 * <p>IMBlocker 是可选依赖，所以这里全部用反射调用（不编译期依赖它）；</p>
 * 任何一步失败都只记一次日志，不影响游戏。</p>
 */
public final class ImBlockerCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("imblocker");
	private static boolean broken;
	private static boolean warned;

	private static Object proxy;
	private static Method setFocusOwner;
	private static Method updateIMState;
	private static Object englishState;

	/** 当前输入框的几何信息（GUI 单位）与光标位置。 */
	private static double scale = 1.0;
	private static int fieldX;
	private static int fieldY;
	private static int fieldW;
	private static int fieldH;
	private static int caretX;

	private ImBlockerCompat() {
	}

	/** 输入框获得焦点：告诉 IMBlocker「现在在输入文本」。 */
	public static void onFieldFocused(int x, int y, int w, int h, int caret, double guiScale) {
		if (!LOADED || broken) {
			return;
		}

		try {
			init();
			scale = guiScale <= 0 ? 1.0 : guiScale;
			fieldX = x;
			fieldY = y;
			fieldW = w;
			fieldH = h;
			caretX = caret;
			setFocusOwner.invoke(null, proxy);
			updateIMState.invoke(englishState, proxy);
		} catch (Throwable t) {
			fail(t);
		}
	}

	/** 输入框失去焦点（或界面关闭）：把焦点交还给 IMBlocker。 */
	public static void onFieldBlurred() {
		if (!LOADED || broken || proxy == null) {
			return;
		}

		try {
			setFocusOwner.invoke(null, (Object) null);
		} catch (Throwable t) {
			fail(t);
		}
	}

	private static void init() throws Exception {
		if (proxy != null) {
			return;
		}

		ClassLoader loader = ImBlockerCompat.class.getClassLoader();
		Class<?> focusableObject = Class.forName(
				"io.github.reserveword.imblocker.common.gui.FocusableObject", false, loader);
		Class<?> focusManager = Class.forName(
				"io.github.reserveword.imblocker.common.gui.FocusManager", false, loader);
		Class<?> configClass = Class.forName(
				"io.github.reserveword.imblocker.common.IMBlockerConfig", false, loader);

		final Constructor<?> rectangle = Class.forName(
				"io.github.reserveword.imblocker.common.gui.Rectangle", false, loader)
				.getConstructor(double.class, double.class, double.class, double.class, double.class);
		final Constructor<?> point = Class.forName(
				"io.github.reserveword.imblocker.common.gui.Point", false, loader)
				.getConstructor(double.class, double.class, double.class);

		setFocusOwner = focusManager.getMethod("setFocusOwner", focusableObject);

		Object config = configClass.getField("INSTANCE").get(null);
		englishState = config.getClass().getMethod("getEnglishStateImpl").invoke(config);
		updateIMState = englishState.getClass().getMethod("updateIMState", focusableObject);

		InvocationHandler handler = (self, method, args) -> {
			switch (method.getName()) {
				case "getPreferredState":
					return Boolean.TRUE;
				case "getPreferredEnglishState":
					return Boolean.FALSE;
				case "getBoundsAbs":
					return rectangle.newInstance(scale, (double) fieldX, (double) fieldY,
							(double) fieldW, (double) fieldH);
				case "getCaretPos":
					return point.newInstance(scale, (double) caretX, 0.0);
				case "getFontHeight":
					return 9;
				case "getGuiScale":
					return scale;
				case "isTrulyFocused":
					return true;
				case "deliverFocus":
					setFocusOwner.invoke(null, self);
					updateIMState.invoke(englishState, self);
					return null;
				case "updateIMState":
					updateIMState.invoke(englishState, self);
					return null;
				case "lostFocus":
					setFocusOwner.invoke(null, (Object) null);
					return null;
				case "toString":
					return "MapDrawTextField";
				case "hashCode":
					return System.identityHashCode(self);
				case "equals":
					return self == args[0];
				default:
					return null;
			}
		};

		proxy = Proxy.newProxyInstance(loader, new Class<?>[]{focusableObject}, handler);
	}

	private static void fail(Throwable t) {
		broken = true;

		if (!warned) {
			warned = true;
			MapDrawClient.LOGGER.warn("[MapDrawClient] IMBlocker 兼容失败（不影响游戏，输入法需手动切换）: {}", t.toString());
		}
	}
}
