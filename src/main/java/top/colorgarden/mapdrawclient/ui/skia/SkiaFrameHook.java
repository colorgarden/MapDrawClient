package top.colorgarden.mapdrawclient.ui.skia;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * 帧末绘制钩子：Skija 直接写帧缓冲，必须等 Minecraft 提交完 GUI 批处理再画，否则会被覆盖。
 */
public final class SkiaFrameHook {
	private static Runnable pending;

	private SkiaFrameHook() {
	}

	/** 登记本帧要执行的绘制（帧尾执行）。 */
	public static void request(Runnable runnable) {
		pending = runnable;
	}

	/** 由 mixin 在帧尾调用。 */
	public static void onFrameEnd() {
		Runnable runnable = pending;

		if (runnable == null) {
			return;
		}

		pending = null;

		try {
			runnable.run();
		} catch (Throwable t) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] Skija 帧末绘制失败（已忽略）: {}", t.toString());
		}
	}
}