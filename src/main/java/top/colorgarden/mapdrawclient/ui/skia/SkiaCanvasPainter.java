package top.colorgarden.mapdrawclient.ui.skia;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;

import top.colorgarden.mapdrawclient.MapDrawClient;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;
import top.colorgarden.mapdrawclient.ui.UiKit;

/**
 * 帧尾用 Skija 直接把画布画到帧缓冲（不走纹理中转）。
 *
 * <p>坐标处理：先把画布变换成「GUI 坐标、Y 向下」，之后就完全按界面坐标画
 * （Skija 的 surface 是 BOTTOM_LEFT 原点 + 帧缓冲像素，所以要 scale(guiScale, -guiScale) 再平移）。</p>
 */
public final class SkiaCanvasPainter {
	public static final SkiaCanvasPainter INSTANCE = new SkiaCanvasPainter();

	private final Paint paint = new Paint();
	/** 画图像专用：纯白不透明，避免被棋盘格留下的颜色染色。 */
	private final Paint imagePaint = new Paint().setColor(0xFFFFFFFF);
	private Image image;
	private byte[] bytes;
	private byte[] cachedPixels;
	/** 诊断计数。 */
	private int diagFrames;

	private SkiaCanvasPainter() {
	}

	/** 界面每帧调用：登记帧尾绘制。 */
	public void request(CanvasData canvas, int viewX, int viewY, int viewW, int viewH,
			int originX, int originY, int cw, int cellPx, boolean checker, int background, double guiScale) {
		SkiaFrameHook.request(() -> {
			try {
				this.draw(canvas, viewX, viewY, viewW, viewH, originX, originY, cw, cellPx, checker, background, guiScale);
			} catch (Throwable t) {
				MapDrawClient.LOGGER.warn("[MapDrawClient] Skija 画布绘制失败（忽略）: {}", t.toString());
			}
		});
	}

	private void draw(CanvasData canvas, int viewX, int viewY, int viewW, int viewH,
			int originX, int originY, int cw, int cellPx, boolean checker, int background, double guiScale) {
		SkiaGlContext context = SkiaGlContext.INSTANCE;
		context.ensure();

		if (!context.ready()) {
			if (++this.diagFrames % 60 == 0) {
				MapDrawClient.LOGGER.warn("[MapDrawClient] Skija 上下文未就绪（绘制跳过）");
			}

			return;
		}

		if (++this.diagFrames % 60 == 0) {
			MapDrawClient.LOGGER.info("[MapDrawClient] Skija 绘制中: 表面 {}x{}, 视口 ({},{},{}x{}), 画布 ({},{},{}), guiScale={}",
					context.width(), context.height(), viewX, viewY, viewW, viewH, originX, originY, cw, guiScale);
		}

		this.updateImage(canvas);
		context.beginFrame();


		Canvas sk = context.canvas();
		float scale = (float) (guiScale <= 0 ? 1.0 : guiScale);
		sk.save();
		// 帧缓冲像素 + BOTTOM_LEFT → 换成 GUI 坐标、Y 向下
		sk.scale(scale, -scale);
		sk.translate(0.0F, -(context.height() / scale));
		// 只画视口范围
		sk.clipRect(Rect.makeXYWH(viewX, viewY, viewW, viewH));

		if (checker) {
			int mapCell = Math.max(1, cellPx);
			float cell = mapCell;
			int cells = MapDrawProtocol.CANVAS_W / mapCell + 1;

			for (int gy = 0; gy <= cells; gy++) {
				for (int gx = 0; gx <= cells; gx++) {
					float px = originX + gx * cell;
					float py = originY + gy * cell;

					if (px + cell < viewX || px > viewX + viewW || py + cell < viewY || py > viewY + viewH) {
						continue;
					}

					this.paint.setColor((((gx + gy) & 1) == 0) ? UiKit.CHECK_A : UiKit.CHECK_B);
					sk.drawRect(Rect.makeXYWH(px, py, cell, cell), this.paint);
				}
			}
		} else {
			this.paint.setColor(background);
			sk.drawRect(Rect.makeXYWH(originX, originY, cw, cw), this.paint);
		}

		if (this.image != null) {
			// 画图像用纯白 Paint（不然会被上一次 setColor 染色）
			sk.drawImageRect(this.image,
					Rect.makeXYWH(0, 0, MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H),
					Rect.makeXYWH(originX, originY, cw, cw), this.imagePaint);
		}

		sk.restore();
		context.endFrame();
	}

	/** 128x128 调色板 → Skija 图像（变了才重建；完整比较，避免只比两个字节导致滞后）。 */
	private void updateImage(CanvasData canvas) {
		byte[] pixels = canvas.pixels();

		if (this.cachedPixels != null && java.util.Arrays.equals(this.cachedPixels, pixels)) {
			return;
		}

		if (this.bytes == null) {
			this.bytes = new byte[MapDrawProtocol.CANVAS_W * MapDrawProtocol.CANVAS_H * 4];
		}

		for (int i = 0; i < MapDrawProtocol.CANVAS_W * MapDrawProtocol.CANVAS_H; i++) {
			int argb = MapPalette.argb(pixels[i]);
			int o = i * 4;
			this.bytes[o] = (byte) (argb & 0xFF);
			this.bytes[o + 1] = (byte) ((argb >> 8) & 0xFF);
			this.bytes[o + 2] = (byte) ((argb >> 16) & 0xFF);
			this.bytes[o + 3] = (byte) (argb >>> 24);
		}

		if (this.image != null) {
			this.image.close();
		}

		this.image = Image.makeRaster(
				ImageInfo.makeN32Premul(MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H),
				this.bytes, MapDrawProtocol.CANVAS_W * 4L);
		this.cachedPixels = pixels.clone();
	}
}