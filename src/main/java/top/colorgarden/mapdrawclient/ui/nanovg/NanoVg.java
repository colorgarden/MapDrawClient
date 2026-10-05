package top.colorgarden.mapdrawclient.ui.nanovg;

import org.lwjgl.nanovg.NanoVGGL3;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * NanoVG 上下文（LWJGL 官方 2D 矢量渲染，GPU 加速）。
 *
 * <p>只负责创建 / 销毁上下文与自检；绘制由后续的画布渲染器使用。
 * 必须在渲染线程、且有当前 GL 上下文时创建。</p>
 */
public final class NanoVg {
	private static long handle;
	private static boolean failed;
	private static boolean logged;

	private NanoVg() {
	}

	/** 取得（首次调用时创建）NanoVG 上下文；失败返回 0。 */
	public static long handle() {
		if (handle != 0 || failed) {
			return handle;
		}

		try {
			handle = NanoVGGL3.nvgCreate(NanoVGGL3.NVG_ANTIALIAS);

			if (handle == 0) {
				failed = true;
				MapDrawClient.LOGGER.warn("[MapDrawClient] NanoVG 上下文创建失败（返回 0）");
			} else if (!logged) {
				logged = true;
				MapDrawClient.LOGGER.info("[MapDrawClient] NanoVG 已就绪（GPU 2D 渲染可用）");
			}
		} catch (Throwable t) {
			failed = true;
			MapDrawClient.LOGGER.warn("[MapDrawClient] NanoVG 初始化失败: {}", t.toString());
		}

		return handle;
	}

	public static void destroy() {
		if (handle != 0) {
			try {
				NanoVGGL3.nvgDelete(handle);
			} catch (Throwable ignored) {
				// 忽略
			}

			handle = 0;
		}
	}
}