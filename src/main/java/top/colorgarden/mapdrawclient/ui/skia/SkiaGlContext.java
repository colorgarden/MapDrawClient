package top.colorgarden.mapdrawclient.ui.skia;

import com.mojang.blaze3d.systems.RenderSystem;

import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceOrigin;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * Skija 的 GL 上下文：直接画到 Minecraft 的帧缓冲（不走纹理中转）。
 *
 * <p>骨架照抄 xiushabei/Musangclient（master 分支 26.x 路径）。</p>
 *
 * <p><b>GL 状态保护是重点</b>：Skija 会改一堆 GL 状态且不还原，尤其是
 * {@code GL_UNPACK_*}（不还原会把 MC 的字形图集写坏 → 同一字形逐行错位）。
 * 所以 beginFrame/endFrame 必须成对调用，且按下面的清单重置/恢复。</p>
 *
 * <p>只在 26.x 编译（帧缓冲 id 的获取方式 26.x 与 1.21.x 完全不同）。</p>
 */
public final class SkiaGlContext {
	public static final SkiaGlContext INSTANCE = new SkiaGlContext();

	private DirectContext context;
	private BackendRenderTarget renderTarget;
	private Surface surface;
	private Canvas canvas;
	private int lastWidth = -1;
	private int lastHeight = -1;
	private int lastFbId = -1;
	private int diagBails;

	private SkiaGlContext() {
	}

	public boolean ready() {
		return this.surface != null && this.canvas != null;
	}

	public Canvas canvas() {
		return this.canvas;
	}

	public int width() {
		return this.lastWidth;
	}

	public int height() {
		return this.lastHeight;
	}

	/** 确保上下文与 surface 就绪（尺寸/帧缓冲变了会重建）。 */
	public void ensure() {
		try {
			if (this.context == null) {
				this.context = DirectContext.makeGL();
			}

			int width = framebufferWidth();
			int height = framebufferHeight();
			int fbId = framebufferId();

			// 注意：FBO 0 是「默认帧缓冲」，是合法值 —— 之前写成 fbId <= 0 直接 return，导致永远建不出 surface
			if (width <= 0 || height <= 0 || fbId < 0) {
				if (++this.diagBails % 60 == 1) {
					MapDrawClient.LOGGER.warn("[MapDrawClient] Skija 跳过: 帧缓冲尺寸/ID 无效 width={} height={} fbId={}", width, height, fbId);
				}

				return;
			}

			if (this.surface == null || width != this.lastWidth || height != this.lastHeight || fbId != this.lastFbId) {
				this.recreate(width, height, fbId);
			}
		} catch (Throwable t) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] Skija GL 上下文初始化失败（回退逐像素）: {}", t.toString());
			this.close();
		}
	}

	private void recreate(int width, int height, int fbId) {
		if (this.surface != null) {
			this.surface.close();
		}

		if (this.renderTarget != null) {
			this.renderTarget.close();
		}

		this.renderTarget = BackendRenderTarget.makeGL(width, height, 0, 8, fbId, 32856);
		this.surface = Surface.makeFromBackendRenderTarget(this.context, this.renderTarget,
				SurfaceOrigin.BOTTOM_LEFT, ColorType.RGBA_8888, ColorSpace.getSRGB());
		this.canvas = this.surface.getCanvas();
		this.lastWidth = width;
		this.lastHeight = height;
		this.lastFbId = fbId;
		MapDrawClient.LOGGER.info("[MapDrawClient] Skija 帧缓冲就绪: {}x{} fbo={}", width, height, fbId);
	}

	/** 进入绘制前：把 Skija 可能依赖的状态重置干净。 */
	public void beginFrame() {
		RenderSystem.assertOnRenderThread();
		GL11.glPixelStorei(3314, 0);
		GL11.glPixelStorei(3316, 0);
		GL11.glPixelStorei(3315, 0);
		GL11.glPixelStorei(3317, 1);
		GL15.glBindBuffer(35052, 0);
		GL13.glActiveTexture(33984);
		GL11.glDisable(2884);

		if (this.context != null) {
			this.context.resetGLAll();
		}
	}

	/** 结束绘制：提交并把 Skija 动过的状态恢复成 Minecraft 期望的样子。 */
	public void endFrame() {
		RenderSystem.assertOnRenderThread();

		if (this.surface != null && this.context != null) {
			this.context.flushAndSubmit(this.surface);
		}

		GL33.glBindSampler(0, 0);
		GL11.glDisable(3042);
		GL11.glBlendFunc(770, 771);
		GL14.glBlendEquation(32774);
		GL11.glColorMask(true, true, true, true);
		GL11.glDepthMask(true);
		GL11.glDisable(3089);
		GL11.glDisable(2929);
		GL13.glActiveTexture(33984);
		GL11.glDisable(2884);
		GL13.glActiveTexture(33984);
		GL11.glBindTexture(3553, 0);
		GL33.glBindSampler(0, 0);
		GL11.glPixelStorei(3314, 0);
		GL11.glPixelStorei(3316, 0);
		GL11.glPixelStorei(3315, 0);
		GL11.glPixelStorei(3317, 4);
		GL15.glBindBuffer(35052, 0);
	}

	private void close() {
		try {
			if (this.surface != null) {
				this.surface.close();
			}

			if (this.renderTarget != null) {
				this.renderTarget.close();
			}
		} catch (Throwable ignored) {
			// 忽略
		}

		this.surface = null;
		this.renderTarget = null;
		this.canvas = null;
		this.lastWidth = -1;
		this.lastHeight = -1;
		this.lastFbId = -1;
	}

	// ------------------------------------------------------------------
	// 帧缓冲信息
	// ------------------------------------------------------------------
	// 26.2 没有 Minecraft.getMainRenderTarget()（那是 26.1.2 的 API）。
	// 帧尾绘制时目标就是「窗口的默认帧缓冲」= FBO 0，尺寸用 GLFW 直接问窗口。
	private static long windowHandle() {
		try {
			// 当前 GLFW 上下文句柄：比反射 Minecraft.Window 更可靠
			return org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
		} catch (Throwable t) {
			return 0L;
		}
	}

	private static int framebufferWidth() {
		return fbSize(0);
	}

	private static int framebufferHeight() {
		return fbSize(1);
	}

	private static int fbSize(int index) {
		try {
			long handle = windowHandle();

			if (handle == 0L) {
				return 0;
			}

			int[] w = new int[1];
			int[] h = new int[1];
			org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(handle, w, h);
			return index == 0 ? w[0] : h[0];
		} catch (Throwable t) {
			return 0;
		}
	}

	/** 默认帧缓冲。 */
	private static int framebufferId() {
		return 0;
	}
}