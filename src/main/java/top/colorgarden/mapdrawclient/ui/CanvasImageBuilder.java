package top.colorgarden.mapdrawclient.ui;

import com.mojang.blaze3d.platform.NativeImage;

import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 把「视口范围内的画布内容」生成成一张 NativeImage（棋盘格 + 像素 + 网格）。
 *
 * <p>只做像素生成，不碰 GL：生成后交给 {@link CanvasImageTexture} 用 MC 自己的
 * GpuTexture 管线上传 + blit（不调用任何 gl* 函数，避免驱动级崩溃）。</p>
 */
public final class CanvasImageBuilder {
	private CanvasImageBuilder() {
	}

	public static void build(NativeImage image, CanvasData canvas, int viewX, int viewY, int w, int h,
			int originX, int originY, float zoom, int cellPx, boolean checker, int background,
			boolean grid, int gridN, int gridColor) {
		byte[] pixels = canvas == null ? null : canvas.pixels();
		float scale = Math.max(0.05F, zoom);
		int cell = Math.max(1, cellPx);
		int gridStep = Math.max(1, gridN);
		boolean drawGrid = grid && gridStep * scale >= 4.0F;

		for (int ty = 0; ty < h; ty++) {
			int screenY = viewY + ty;
			int mapY = (int) Math.floor((screenY - originY) / scale);

			for (int tx = 0; tx < w; tx++) {
				int screenX = viewX + tx;
				int mapX = (int) Math.floor((screenX - originX) / scale);
				int argb = 0;

				if (mapX >= 0 && mapX < MapDrawProtocol.CANVAS_W && mapY >= 0 && mapY < MapDrawProtocol.CANVAS_H) {
					byte value = pixels == null ? 0 : pixels[mapY * MapDrawProtocol.CANVAS_W + mapX];

					if (value == 0) {
						if (checker) {
							// 棋盘格锚定在画布坐标上（不是屏幕坐标），所以跟着画布一起平移/缩放
							int cx = (int) Math.floor((screenX - originX) / cell);
							int cy = (int) Math.floor((screenY - originY) / cell);
							argb = (((cx + cy) & 1) == 0) ? UiKit.CHECK_A : UiKit.CHECK_B;
						} else {
							argb = background;
						}
					} else {
						argb = MapPalette.argb(value);
					}

					if (drawGrid && argb != 0) {
						// 网格按「屏幕像素」画：每 gridStep*scale 个 GUI 像素一条 1 像素宽的线
						int stepPx = Math.max(2, Math.round(gridStep * scale));
						int sx = Math.floorMod(screenX - originX, stepPx);
						int sy = Math.floorMod(screenY - originY, stepPx);

						if (sx == 0 || sy == 0) {
							argb = gridColor;
						}
					}
				} else {
					argb = 0;
				}

				image.setPixel(tx, ty, argb);
			}
		}
	}
}