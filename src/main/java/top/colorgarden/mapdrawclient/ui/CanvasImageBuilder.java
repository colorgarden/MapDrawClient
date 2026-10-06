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
		// 棋盘格的地图像素格宽（把屏幕格宽还原成地图像素），保证格子边界与像素采样同源
		int cellMap = Math.max(1, Math.round(cell / scale));
		int gridStep = Math.max(1, gridN);
		// 网格由 BoardScreen 的绘制层负责（这里不再画，避免两套网格）

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
							float cellF = Math.max(1.0F, cellMap * scale);
							int cx = (int) Math.floor((screenX - originX) / cellF);
							int cy = (int) Math.floor((screenY - originY) / cellF);
							argb = (((cx + cy) & 1) == 0) ? UiKit.CHECK_A : UiKit.CHECK_B;
						} else {
							argb = background;
						}
					} else {
						argb = MapPalette.argb(value);
					}

				} else {
					argb = 0;
				}

				//#if MC >= 12102
				image.setPixel(tx, ty, argb);
				//#else
				//$$ // 1.21.1 / 1.20.6：只有 setPixelRGBA，且通道顺序是 ABGR
				//$$ image.setPixelRGBA(tx, ty, ((argb & 0xFF) << 16) | (argb & 0xFF00) | ((argb >> 16) & 0xFF) | (argb & 0xFF000000));
				//#endif
			}
		}
	}
	/**
	 * 多联大图（拼接）版：把矩阵里每个节点的画布按 gridCol/gridRow 贴到整张大画布上。
	 *
	 * @param nodes 每项 {gridCol, gridRow, CanvasData}
	 */
	public static void buildMatrix(NativeImage image, java.util.List<Object[]> nodes, int viewX, int viewY, int w, int h,
			int originX, int originY, float zoom, int cellPx, boolean checker, int background) {
		float scale = Math.max(0.05F, zoom);
		int cell = Math.max(1, cellPx);
		int cellMap = Math.max(1, Math.round(cell / scale));

		for (int ty = 0; ty < h; ty++) {
			int screenY = viewY + ty;
			int globalMapY = (int) Math.floor((screenY - originY) / scale);

			for (int tx = 0; tx < w; tx++) {
				int screenX = viewX + tx;
				int globalMapX = (int) Math.floor((screenX - originX) / scale);
				int argb = 0;

				if (globalMapX >= 0 && globalMapY >= 0) {
					int nodeCol = globalMapX / MapDrawProtocol.CANVAS_W;
					int nodeRow = globalMapY / MapDrawProtocol.CANVAS_H;
					int localX = globalMapX % MapDrawProtocol.CANVAS_W;
					int localY = globalMapY % MapDrawProtocol.CANVAS_H;
					byte[] pixels = null;

					for (Object[] node : nodes) {
						if ((Integer) node[0] == nodeCol && (Integer) node[1] == nodeRow) {
							CanvasData c = (CanvasData) node[2];

							if (c != null) {
								pixels = c.pixels();
							}

							break;
						}
					}

					if (pixels != null) {
						byte value = pixels[localY * MapDrawProtocol.CANVAS_W + localX];

						if (value == 0) {
							if (checker) {
								float cellF = Math.max(1.0F, cellMap * scale);
								int cx = (int) Math.floor((screenX - originX) / cellF);
								int cy = (int) Math.floor((screenY - originY) / cellF);
								argb = (((cx + cy) & 1) == 0) ? UiKit.CHECK_A : UiKit.CHECK_B;
							} else {
								argb = background;
							}
						} else {
							argb = MapPalette.argb(value);
						}
					}
				}

				image.setPixel(tx, ty, argb);
			}
		}
	}
}