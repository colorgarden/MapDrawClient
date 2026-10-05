package top.colorgarden.mapdrawclient.input;

import java.lang.reflect.Method;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * 数位板压感（Windows / Wintab，经 JNA 调用 {@code wintab32.dll}）。
 *
 * <p>Minecraft 用的 GLFW 没有任何笔压 API，所以只能直接问驱动要数据。这里用 Wintab：
 * 它是 Wacom / Huion / XP-Pen 驱动都带的原生接口，而且和系统鼠标<b>并存</b>
 * （用 {@code CXO_SYSTEM} 打开上下文，游戏里的鼠标位置照常来自 GLFW）。</p>
 *
 * <p>拿不到就静默关闭（没驱动、不是 Windows、没有板子）—— 不影响任何其它功能。</p>
 */
public final class TabletInput {
	public static final TabletInput INSTANCE = new TabletInput();

	/** 压力无效（鼠标 / 没板子）。 */
	public static final float NO_PRESSURE = -1.0F;

	// Wintab 常量
	private static final int WTI_DEVICES = 100;
	private static final int DVC_NPRESSURE = 15;
	private static final int WTI_DEFCONTEXT = 3;
	private static final int PK_STATUS = 0x0002;
	private static final int PK_X = 0x0080;
	private static final int PK_Y = 0x0100;
	private static final int PK_BUTTONS = 0x0040;
	private static final int PK_NORMAL_PRESSURE = 0x0400;
	private static final int CXO_SYSTEM = 0x0001;

	private volatile float pressure = NO_PRESSURE;
	private volatile long pressureAt;
	private volatile boolean available;
	private volatile String deviceName = "";
	private volatile int pressureMax = 1023;
	private boolean started;

	private TabletInput() {
	}

	/** 是否成功打开了数位板上下文。 */
	public boolean available() {
		return this.available;
	}

	public String deviceName() {
		return this.deviceName;
	}

	/** 归一化压力 0..1；{@link #NO_PRESSURE} 表示当前没有笔（例如鼠标）。 */
	public float pressure() {
		// 超过 250ms 没新包就算「笔抬起了」
		if (System.currentTimeMillis() - this.pressureAt > 250L) {
			return NO_PRESSURE;
		}

		return this.pressure;
	}

	/** 压力曲线：0=线性，1=偏轻（细），2=偏重（粗）。 */
	public static float applyCurve(float p, int curve) {
		float v = p < 0.0F ? 0.0F : (p > 1.0F ? 1.0F : p);

		return switch (curve) {
			case 1 -> v * v;
			case 2 -> (float) Math.sqrt(v);
			default -> v;
		};
	}

	/** 启动后台轮询线程（只在 Windows 且能加载 wintab32 时真正生效）。 */
	public synchronized void start() {
		if (this.started) {
			return;
		}

		this.started = true;

		if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
			return;
		}

		Thread thread = new Thread(this::run, "MapDrawClient-Tablet");
		thread.setDaemon(true);
		thread.start();
	}

	private void run() {
		Wintab wintab;

		try {
			wintab = Native.load("wintab32", Wintab.class);
		} catch (Throwable t) {
			MapDrawClient.LOGGER.info("[MapDrawClient] 数位板：没找到 wintab32.dll（未安装板子驱动），压感关闭");
			return;
		}

		try {
			Memory axis = new Memory(64);
			int ok = wintab.WTInfoA(WTI_DEVICES, DVC_NPRESSURE, axis);

			if (ok > 0) {
				int max = axis.getInt(4);   // AXIS.axMax

				if (max > 0) {
					this.pressureMax = max;
				}
			}

			long hwnd = windowHandle();

			if (hwnd == 0L) {
				MapDrawClient.LOGGER.info("[MapDrawClient] 数位板：拿不到游戏窗口句柄，压感关闭");
				return;
			}

			Memory logContext = new Memory(256);

			if (wintab.WTInfoA(WTI_DEFCONTEXT, 0, logContext) <= 0) {
				MapDrawClient.LOGGER.info("[MapDrawClient] 数位板：WTInfo(WTI_DEFCONTEXT) 失败，压感关闭");
				return;
			}

			// lcOptions / lcPktData / lcPktMode（偏移见 LOGCONTEXT 布局）
			logContext.setInt(40, CXO_SYSTEM);
			logContext.setInt(64, PK_STATUS | PK_X | PK_Y | PK_BUTTONS | PK_NORMAL_PRESSURE);
			logContext.setInt(68, 0);

			com.sun.jna.Pointer hctx = wintab.WTOpenA(new com.sun.jna.Pointer(hwnd), logContext, true);

			if (hctx == null || com.sun.jna.Pointer.nativeValue(hctx) == 0L) {
				MapDrawClient.LOGGER.info("[MapDrawClient] 数位板：WTOpen 失败，压感关闭");
				return;
			}

			this.available = true;
			MapDrawClient.LOGGER.info("[MapDrawClient] 数位板压感已启用（Wintab，压力上限 {}）", this.pressureMax);

			Memory packet = new Memory(256);

			while (true) {
				if (wintab.WTPacket(hctx, 0, packet) > 0) {
					int raw = packet.getInt(40);   // PACKET.pkNormalPressure
					this.pressure = Math.max(0.0F, Math.min(1.0F, raw / (float) this.pressureMax));
					this.pressureAt = System.currentTimeMillis();
				}

				Thread.sleep(2L);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Throwable t) {
			this.available = false;
			MapDrawClient.LOGGER.info("[MapDrawClient] 数位板压感初始化失败（忽略）：{}", t.toString());
		}
	}

	/** 用 LWJGL 的 GLFWNativeWin32 拿 HWND（非 Windows 上这个类不存在，反射 + try 包住）。 */
	private static long windowHandle() {
		try {
			Object window = net.minecraft.client.Minecraft.getInstance().getWindow();
			Method handle = window.getClass().getMethod("getWindow");
			long glfwHandle = (Long) handle.invoke(window);
			Class<?> nativeWin32 = Class.forName("org.lwjgl.glfw.GLFWNativeWin32");
			Method get = nativeWin32.getMethod("glfwGetWin32Window", long.class);
			return (Long) get.invoke(null, glfwHandle);
		} catch (Throwable t) {
			return 0L;
		}
	}

	/** wintab32.dll 的入口。 */
	private interface Wintab extends Library {
		int WTInfoA(int category, int index, com.sun.jna.Pointer buffer);

		com.sun.jna.Pointer WTOpenA(com.sun.jna.Pointer hwnd, com.sun.jna.Pointer logContext, boolean enable);

		int WTPacket(com.sun.jna.Pointer hctx, int serial, com.sun.jna.Pointer packet);
	}
}