package top.colorgarden.mapdrawclient.input;

import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/**
 * 屏幕取色（含游戏窗口以外的区域）。
 *
 * <p>原来只用 AWT：{@code MouseInfo.getPointerInfo()} 拿到的位置是 <b>AWT 自己缓存的</b>，
 * 而游戏用 GLFW 直接读鼠标、鼠标事件不会流到 AWT —— 所以那个坐标是过期/错的，
 * 取色自然「根本不能用」。</p>
 *
 * <p>现在优先走 Win32：{@code GetCursorPos} 拿系统光标（永远准确），
 * {@code GetDC(NULL) + GetPixel} 读屏幕像素（含窗口外、含 DWM 合成后的画面）。
 * 非 Windows 或调用失败时回退到 AWT Robot。</p>
 */
public final class ScreenColor {
	private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
	private static User32 user32;
	private static Robot robot;
	private static boolean initDone;
	private static boolean initOk;

	private ScreenColor() {
	}

	/** 是否可用（Win32 或 AWT 至少一个能起来）。 */
	public static synchronized boolean available() {
		init();

		return initOk;
	}

	private static synchronized void init() {
		if (initDone) {
			return;
		}

		initDone = true;

		if (IS_WINDOWS) {
			try {
				user32 = Native.load("user32", User32.class);
				// 试调一次，确认真的能用
				Memory point = new Memory(8);

				if (user32.GetCursorPos(point)) {
					initOk = true;
					return;
				}
			} catch (Throwable ignored) {
				user32 = null;
			}
		}

		try {
			robot = new Robot();
			initOk = true;
		} catch (Throwable ignored) {
			initOk = false;
		}
	}

	/** 系统光标位置（屏幕坐标）；拿不到返回 null。 */
	public static int[] cursor() {
		init();

		if (user32 != null) {
			try {
				Memory point = new Memory(8);

				if (user32.GetCursorPos(point)) {
					return new int[]{point.getInt(0), point.getInt(4)};
				}
			} catch (Throwable ignored) {
				// 落到 AWT
			}
		}

		try {
			Point point = MouseInfo.getPointerInfo() == null ? null : MouseInfo.getPointerInfo().getLocation();
			return point == null ? null : new int[]{point.x, point.y};
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** 读屏幕像素，返回 0xFFRRGGBB；失败返回 -1。 */
	public static int argbAt(int x, int y) {
		init();

		if (user32 != null) {
			try {
				Pointer dc = user32.GetDC(null);

				if (dc != null) {
					int colorRef = user32.GetPixel(dc, x, y);
					user32.ReleaseDC(null, dc);

					if (colorRef != -1) {
						// COLORREF 是 0x00BBGGRR
						int r = colorRef & 0xFF;
						int g = (colorRef >> 8) & 0xFF;
						int b = (colorRef >> 16) & 0xFF;
						return 0xFF000000 | (r << 16) | (g << 8) | b;
					}
				}
			} catch (Throwable ignored) {
				// 落到 AWT
			}
		}

		try {
			if (robot == null) {
				robot = new Robot();
			}

			Color color = robot.getPixelColor(x, y);
			return 0xFF000000 | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
		} catch (Throwable ignored) {
			return -1;
		}
	}

	/** user32 的最小绑定。 */
	private interface User32 extends Library {
		boolean GetCursorPos(Pointer point);

		Pointer GetDC(Pointer hwnd);

		int GetPixel(Pointer hdc, int x, int y);

		int ReleaseDC(Pointer hwnd, Pointer hdc);
	}
}