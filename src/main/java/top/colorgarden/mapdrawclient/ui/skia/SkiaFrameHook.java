package top.colorgarden.mapdrawclient.ui.skia;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * 帧末绘制钩子：Skija 直接写帧缓冲，必须等 Minecraft 提交完 GUI 批处理再画，否则会被覆盖。
 *
 * <p>带诊断日志（每 60 帧一条）：确认「界面有没有登记请求」与「帧尾有没有被调用」。</p>
 */
public final class SkiaFrameHook {
	private static Runnable pending;
	private static int frameCalls;
	private static int requestCalls;

	private SkiaFrameHook() {
	}

	/** 登记本帧要执行的绘制（帧尾执行）。 */
	public static void request(Runnable runnable) {
		pending = runnable;
		requestCalls++;
	}

	/** 由 mixin 在帧尾调用。 */
	public static void onFrameEnd() {
		frameCalls++;

		if (frameCalls % 60 == 0) {
			MapDrawClient.LOGGER.info("[MapDrawClient] 帧尾钩子: 已调用 {} 次, 期间收到 {} 次登记, 本次{}请求",
					frameCalls, requestCalls, pending == null ? "无" : "有");
		}

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