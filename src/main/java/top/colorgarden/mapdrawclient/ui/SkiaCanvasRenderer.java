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
	private static final int TEX = 1024;

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
	private int cachedOffsetX = Integer.MIN_VALUE;
	private int cachedOffsetY = Integer.MIN_VALUE;
	private int cachedCw = -1;

	/**
	 * 把画布的**可见部分**画到视口里（每次只处理视口大小的像素，不再整张 2048² 上传）。
	 *
	 * @param viewX/viewY/viewW/viewH 画布视口（屏幕坐标）
	 * @param originX/originY 画布左上角在屏幕上的位置
	 */
	public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, CanvasData canvas,
			int viewX, int viewY, int viewW, int viewH,
			int originX, int originY, int cw, int cellPx, boolean checker, int background) {
		if (viewW <= 0 || viewH <= 0) {
			return;
		}

		this.ensure();

		int w = Math.min(viewW, TEX);
		int h = Math.min(viewH, TEX);
		this.redraw(canvas, w, h, originX - viewX, originY - viewY, cw, cellPx, checker, background);
		Compat.blitSub(g, this.id, viewX, viewY, w, h, w / (float) TEX, h / (float) TEX);
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

	private void redraw(CanvasData canvas, int w, int h, int offX, int offY, int cw, int cellPx,
			boolean checker, int background) {
		byte[] pixels = canvas.pixels();
		boolean same = this.cachedSize == w * 4096 + h
				&& this.cachedCell == cellPx
				&& this.cachedChecker == checker
				&& this.cachedBackground == background
				&& this.cachedOffsetX == offX
				&& this.cachedOffsetY == offY
				&& this.cachedCw == cw
				&& this.cachedPixels != null
				&& Arrays.equals(this.cachedPixels, pixels);

		if (same) {
			return;
		}

		this.updateImage(pixels);

		// 画布在视口坐标系里的位置
		this.canvas.clear(0);
		float cx = offX;
		float cy = offY;

		if (checker) {
			int cell = Math.max(1, cellPx);
			int x0 = Math.max(0, (int) Math.floor(-cx / cell));
			int y0 = Math.max(0, (int) Math.floor(-cy / cell));

			for (int gy = y0; gy <= (int) Math.ceil((h - cy) / cell); gy++) {
				for (int gx = x0; gx <= (int) Math.ceil((w - cx) / cell); gx++) {
					int px = (int) (cx + gx * cell);
					int py = (int) (cy + gy * cell);
					int px2 = px + cell;
					int py2 = py + cell;

					if (px2 <= 0 || py2 <= 0 || px >= w || py >= h) {
						continue;
					}

					this.paint.setColor((((gx + gy) & 1) == 0) ? UiKit.CHECK_A : UiKit.CHECK_B);
					this.canvas.drawRect(Rect.makeXYWH(px, py, cell, cell), this.paint);
				}
			}
		} else {
			this.paint.setColor(background);
			this.canvas.drawRect(Rect.makeXYWH(cx, cy, cw, cw), this.paint);
		}

		if (this.canvasImage != null) {
			this.canvas.drawImageRect(this.canvasImage,
					Rect.makeXYWH(0, 0, MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H),
					Rect.makeXYWH(cx, cy, cw, cw), this.paint);
		}

		this.surface.readPixels(this.readBack, 0, 0);
		byte[] bytes = this.readBack.readPixels();
		NativeImage image = this.texture.getPixels();
		ByteBuffer target = image.getPixelBytes();
		target.clear();
		target.put(bytes, 0, Math.min(bytes.length, target.capacity()));
		this.texture.upload();

		this.cachedPixels = pixels.clone();
		this.cachedSize = w * 4096 + h;
		this.cachedCell = cellPx;
		this.cachedChecker = checker;
		this.cachedBackground = background;
		this.cachedOffsetX = offX;
		this.cachedOffsetY = offY;
		this.cachedCw = cw;
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