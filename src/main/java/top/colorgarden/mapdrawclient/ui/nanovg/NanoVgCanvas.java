package top.colorgarden.mapdrawclient.ui.nanovg;

import java.nio.ByteBuffer;

import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.platform.NativeImage;

import top.colorgarden.mapdrawclient.MapDrawClient;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 用 NanoVG 把「画布」画进一块离屏 FBO，再把像素取回成 NativeImage 交给 MC 的纹理管线。
 *
 * <p>只负责画布区域（棋盘格 + 像素），不碰界面其它部分。</p>
 *
 * <p>为什么用离屏 FBO：NanoVG 需要明确的渲染目标；离屏后由我们控制尺寸与内容，
 * 不用猜 MC 这一帧在渲染哪块缓冲。</p>
 */
public final class NanoVgCanvas {
	private int fbo;
	private int texture;
	private int width;
	private int height;
	private ByteBuffer buffer;
	private NativeImage image;
	private byte[] cachedPixels;
	private int cachedW = -1;
	private int cachedH = -1;

	/** 画布渲染到离屏缓冲，返回可上传的像素（失败返回 null）。 */
	public NativeImage render(CanvasData canvas, int w, int h, int originX, int originY, int cw,
			float zoom, int cellPx, boolean checker, int background) {
		long vg = NanoVg.handle();

		if (vg == 0 || w <= 0 || h <= 0) {
			return null;
		}

		byte[] pixels = canvas.pixels();
		boolean same = this.cachedW == w && this.cachedH == h && this.cachedPixels != null
				&& java.util.Arrays.equals(this.cachedPixels, pixels);

		if (same && this.image != null) {
			return this.image;
		}

		this.ensure(w, h);
		int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
		int[] viewport = new int[4];
		GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

		try {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
			GL11.glViewport(0, 0, w, h);
			NanoVG.nvgBeginFrame(vg, w, h, 1.0F);

			// 画布在视口内的位置：origin 是画布左上角（屏幕坐标），这里换算成离屏坐标
			float canvasX = originX;
			float canvasY = originY;
			float canvasSize = cw;

			if (checker) {
				NanoVG.nvgBeginPath(vg);
				NanoVG.nvgRect(vg, canvasX, canvasY, canvasSize, canvasSize);
				NanoVG.nvgFillColor(vg, NanoVG.nvgRGB((byte) 0x22, (byte) 0x22, (byte) 0x28, org.lwjgl.nanovg.NVGColor.create()));
				NanoVG.nvgFill(vg);
			} else {
				NanoVG.nvgBeginPath(vg);
				NanoVG.nvgRect(vg, canvasX, canvasY, canvasSize, canvasSize);
				NanoVG.nvgFillColor(vg, NanoVG.nvgRGBA((byte) ((background >> 16) & 0xFF), (byte) ((background >> 8) & 0xFF),
						(byte) (background & 0xFF), (byte) ((background >>> 24) & 0xFF), org.lwjgl.nanovg.NVGColor.create()));
				NanoVG.nvgFill(vg);
			}

			// 像素：每个地图像素画一个矩形（zoom 小时合并；这里先直接画，验证通路）
			float pixel = Math.max(1.0F, zoom);
			int cell = Math.max(1, MapDrawProtocol.CANVAS_W);

			for (int y = 0; y < MapDrawProtocol.CANVAS_H; y++) {
				for (int x = 0; x < MapDrawProtocol.CANVAS_W; x++) {
					byte value = pixels[y * MapDrawProtocol.CANVAS_W + x];

					if (value == 0) {
						continue;
					}

					int argb = MapPalette.argb(value);
					org.lwjgl.nanovg.NVGColor color = org.lwjgl.nanovg.NVGColor.create();
					NanoVG.nvgRGB((byte) ((argb >> 16) & 0xFF), (byte) ((argb >> 8) & 0xFF), (byte) (argb & 0xFF), color);
					NanoVG.nvgBeginPath(vg);
					NanoVG.nvgRect(vg, canvasX + x * pixel, canvasY + y * pixel, pixel, pixel);
					NanoVG.nvgFillColor(vg, color);
					NanoVG.nvgFill(vg);
				}
			}

			NanoVG.nvgEndFrame(vg);

			// 取回像素
			if (this.buffer == null) {
				this.buffer = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4);
			}

			GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.buffer);

			if (this.image == null) {
				this.image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
			}

			// GL 是左下原点，NativeImage 是左上原点 → 上下翻转
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int src = ((h - 1 - y) * w + x) * 4;
					int r = this.buffer.get(src) & 0xFF;
					int g = this.buffer.get(src + 1) & 0xFF;
					int b = this.buffer.get(src + 2) & 0xFF;
					int a = this.buffer.get(src + 3) & 0xFF;
					this.image.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
				}
			}

			this.cachedPixels = pixels.clone();
			this.cachedW = w;
			this.cachedH = h;
			return this.image;
		} catch (Throwable t) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] NanoVG 画布渲染失败: {}", t.toString());
			return null;
		} finally {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
			GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
		}
	}

	private void ensure(int w, int h) {
		if (this.fbo != 0 && this.width == w && this.height == h) {
			return;
		}

		this.release();
		this.texture = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		this.fbo = GL30.glGenFramebuffers();
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
		GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.texture, 0);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		this.width = w;
		this.height = h;
		this.buffer = null;
		this.cachedPixels = null;
	}

	private void release() {
		if (this.fbo != 0) {
			GL30.glDeleteFramebuffers(this.fbo);
			this.fbo = 0;
		}

		if (this.texture != 0) {
			GL11.glDeleteTextures(this.texture);
			this.texture = 0;
		}
	}
}