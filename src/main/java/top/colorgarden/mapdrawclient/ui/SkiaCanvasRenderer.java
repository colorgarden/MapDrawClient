package top.colorgarden.mapdrawclient.ui;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.compat.Compat;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 用 Skija（Skia）绘制画布。
 *
 * <p>流程：离屏 raster surface → Skija 画棋盘格 + 画布图像（缩放交给 Skia）→ 读回像素 →
 * 写进 Minecraft 的动态纹理 → 一次 1:1 blit。</p>
 *
 * <p><b>防御措施</b>（上次贴图方案「全坏掉」的教训）：</p>
 * <ul>
 *   <li>纹理与 surface <b>只分配一次</b>（固定 {@value #TEX}×{@value #TEX}），缩放只改 blit 的 UV 子矩形，
 *       绝不每帧销毁/重建纹理</li>
 *   <li>只有内容或尺寸变化才重绘 + 重传</li>
 *   <li>整个类只在 1.21.8+ 参与编译，且由配置 {@code skiaCanvas} 控制，出问题改 false 立刻回退</li>
 * </ul>
 */
public final class SkiaCanvasRenderer {
	/** 固定纹理边长（只分配一次）。 */
	private static final int TEX = 2048;

	private final Identifier id = Compat.makeId("mapdrawclient", "skia_canvas");
	private Surface surface;
	private Canvas canvas;
	private Paint paint;
	private Bitmap readBack;
	private Image canvasImage;
	private byte[] imageBytes;
	private DynamicTexture texture;

	private byte[] cachedPixels;
	private int cachedSize = -1;
	private int cachedCell = -1;
	private boolean cachedChecker;
	private int cachedBackground = -1;

	/** 画整张画布到 (x, y)，边长 size（屏幕像素）。 */
	public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, CanvasData canvas, int x, int y,
			int size, int cellPx, boolean checker, int background) {
		if (size <= 0) {
			return;
		}

		this.ensure();

		int drawn = Math.min(size, TEX);
		this.redraw(canvas, drawn, cellPx, checker, background);
		Compat.blitSub(g, this.id, x, y, size, size, drawn / (float) TEX, drawn / (float) TEX);
	}

	private void ensure() {
		if (this.surface != null) {
			return;
		}

		this.surface = Surface.makeRasterN32Premul(TEX, TEX);
		this.canvas = this.surface.getCanvas();
		this.paint = new Paint();
		this.readBack = new Bitmap();
		this.readBack.allocPixels(ImageInfo.makeN32Premul(TEX, TEX));
		this.texture = new DynamicTexture("mapdrawclient-skia-canvas", TEX, TEX, false);
		Compat.registerTexture(this.id, this.texture);
	}

	private void redraw(CanvasData canvas, int drawn, int cellPx, boolean checker, int background) {
		byte[] pixels = canvas.pixels();
		boolean same = this.cachedSize == drawn
				&& this.cachedCell == cellPx
				&& this.cachedChecker == checker
				&& this.cachedBackground == background
				&& this.cachedPixels != null
				&& Arrays.equals(this.cachedPixels, pixels);

		if (same) {
			return;
		}

		// 1) 画布图像：128x128 调色板 → N32Premul（内存里是 B,G,R,A，与 MC NativeImage 一致）
		this.updateImage(pixels);

		// 2) 棋盘格 + 画布
		this.canvas.clear(0);
		int cell = Math.max(1, cellPx);
		int cells = (drawn + cell - 1) / cell;

		for (int cy = 0; cy < cells; cy++) {
			for (int cx = 0; cx < cells; cx++) {
				if (!checker) {
					continue;
				}

				int argb = (((cx + cy) & 1) == 0) ? UiKit.CHECK_A : UiKit.CHECK_B;
				this.paint.setColor(argb);
				this.canvas.drawRect(Rect.makeXYWH(cx * cell, cy * cell, cell, cell), this.paint);
			}
		}

		if (!checker) {
			this.paint.setColor(background);
			this.canvas.drawRect(Rect.makeXYWH(0, 0, drawn, drawn), this.paint);
		}

		if (this.canvasImage != null) {
			this.canvas.drawImageRect(this.canvasImage,
					Rect.makeXYWH(0, 0, MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H),
					Rect.makeXYWH(0, 0, drawn, drawn), this.paint);
		}

		// 3) 读回像素 → MC 纹理
		this.surface.readPixels(this.readBack, 0, 0);
		byte[] bytes = this.readBack.readPixels();
		NativeImage image = this.texture.getPixels();
		ByteBuffer target = image.getPixelBytes();
		target.clear();
		target.put(bytes, 0, Math.min(bytes.length, target.capacity()));
		this.texture.upload();

		this.cachedPixels = pixels.clone();
		this.cachedSize = drawn;
		this.cachedCell = cellPx;
		this.cachedChecker = checker;
		this.cachedBackground = background;
	}

	/** 把 128x128 的画布像素做成 Skija 图像（变了才重建）。 */
	private void updateImage(byte[] pixels) {
		if (this.imageBytes == null) {
			this.imageBytes = new byte[MapDrawProtocol.CANVAS_W * MapDrawProtocol.CANVAS_H * 4];
		}

		boolean changed = false;

		for (int i = 0; i < MapDrawProtocol.CANVAS_W * MapDrawProtocol.CANVAS_H; i++) {
			int argb = MapPalette.argb(pixels[i]);
			int o = i * 4;

			if (this.imageBytes[o] != (byte) (argb & 0xFF) || this.imageBytes[o + 3] != (byte) (argb >>> 24)) {
				changed = true;
			}

			// B, G, R, A
			this.imageBytes[o] = (byte) (argb & 0xFF);
			this.imageBytes[o + 1] = (byte) ((argb >> 8) & 0xFF);
			this.imageBytes[o + 2] = (byte) ((argb >> 16) & 0xFF);
			this.imageBytes[o + 3] = (byte) (argb >>> 24);
		}

		if (this.canvasImage == null || changed) {
			if (this.canvasImage != null) {
				this.canvasImage.close();
			}

			this.canvasImage = Image.makeRaster(
					ImageInfo.makeN32Premul(MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H),
					this.imageBytes, MapDrawProtocol.CANVAS_W * 4L);
		}
	}
}