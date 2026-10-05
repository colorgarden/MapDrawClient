package top.colorgarden.mapdrawclient.ui.skia;

import java.util.HashSet;
import java.util.Set;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * 帧尾绘制钩子：Skija 直接写帧缓冲，必须在 Minecraft 把画面 blitToScreen 之前画，否则会被覆盖。
 *
 * <p>{@code source} 参数用于诊断「到底是哪个注入点在调用」——每个来源只打一条日志。</p>
 */
public final class SkiaFrameHook {
	private static final Set<String> SEEN = new HashSet<>();
	private static Runnable pending;

	private SkiaFrameHook() {
	}

	public static void request(Runnable runnable) {
		pending = runnable;
	}

	/** 由 mixin 在帧尾调用；{@code source} = 注入点名字。 */
	public static void onFrameEnd(String source) {
		if (SEEN.add(source)) {
			MapDrawClient.LOGGER.info("[MapDrawClient] 帧尾注入点生效: {}", source);
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