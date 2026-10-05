package top.colorgarden.mapdrawclient.ui;

import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.compat.Compat;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 画布渲染：把「视口大小的画布内容」写进一张动态纹理，再用 <b>1:1</b> blit 画出来。
 *
 * <p>要点（之前失败过的坑都避开了）：</p>
 * <ul>
 *   <li><b>纹理尺寸 = 绘制尺寸</b>：不做任何「大纹理 + 子区域 UV」，因此不存在 UV 语义歧义</li>
 *   <li><b>1:1 blit</b>：用经典 9 参重载 {@code blit(id, x, y, u, v, w, h, texW, texH)}（各版本都有）</li>
 *   <li><b>尺寸变化才重建纹理</b>；内容/平移/缩放变化才重写像素并上传</li>
 *   <li>棋盘格与像素<b>同一张图、同一套坐标</b>，不可能错位</li>
 * </ul>
 */
public final class CanvasTextureRenderer {
	/** 纹理边长上限（视口通常远小于此）。 */
	private static final int MAX_SIZE = 1024;

	private final Identifier id = Compat.makeId("mapdrawclient", "canvas_tex");
	private DynamicTexture texture;
	private int texW;
	private int texH;

	private byte[] cachedPixels;
	private int cachedOffX = Integer.MIN_VALUE;
	private int cachedOffY = Integer.MIN_VALUE;
	private int cachedCw = -1;
	private int cachedCell = -1;
	private boolean cachedChecker;
	private int cachedBackground = -1;
	private int cachedW = -1;
	private int cachedH = -1;

	/** 在视口 (viewX, viewY, viewW, viewH) 里画出画布；origin/cw 是画布在 GUI 坐标下的位置与边长。 */
	public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, CanvasData canvas,
			int viewX, int viewY, int viewW, int viewH,
			int originX, int originY, int cw, int cellPx, boolean checker, int background, float zoom) {
		int w = Math.min(viewW, MAX_SIZE);
		int h = Math.min(viewH, MAX_SIZE);

		if (w <= 0 || h <= 0) {
			return;
		}

		this.ensure(w, h);
		this.update(canvas, w, h, originX - viewX, originY - viewY, cw, cellPx, checker, background, zoom);
		// 1:1：纹理尺寸 = 绘制尺寸，UV 取整张
		g.blit(this.id, viewX, viewY, 0, 0, w, h, w, h);
	}

	private void ensure(int w, int h) {
		if (this.texture != null && this.texW == w && this.texH == h) {
			return;
		}

		if (this.texture != null) {
			this.texture.close();
		}

		this.texture = new DynamicTexture("mapdrawclient-canvas", w, h, false);
		this.texW = w;
		this.texH = h;
		this.cachedPixels = null;
		this.cachedW = -1;
		Compat.registerTexture(this.id, this.texture);
	}

	private void update(CanvasData canvas, int w, int h, int offX, int offY, int cw, int cellPx,
			boolean checker, int background, float zoomArg) {
		byte[] pixels = canvas.pixels();
		boolean same = this.cachedW == w && this.cachedH == h
				&& this.cachedOffX == offX && this.cachedOffY == offY
				&& this.cachedCw == cw && this.cachedCell == cellPx
				&& this.cachedChecker == checker && this.cachedBackground == background
				&& this.cachedPixels != null && Arrays.equals(this.cachedPixels, pixels);

		if (same) {
			return;
		}

		// ===== 临时判定测试：无条件填满纯品红，绕过缓存与内容逻辑 =====
		NativeImage image = this.texture.getPixels();

		if (Boolean.getBoolean("mapdrawclient.testMagenta")) {
			for (int ty = 0; ty < h; ty++) {
				for (int tx = 0; tx < w; tx++) {
					image.setPixel(tx, ty, 0xFFFF00FF);
				}
			}

			this.texture.upload();
			this.cachedPixels = pixels.clone();
			this.cachedW = w;
			this.cachedH = h;
			this.cachedOffX = offX;
			this.cachedOffY = offY;
			this.cachedCw = cw;
			this.cachedCell = cellPx;
			this.cachedChecker = checker;
			this.cachedBackground = background;
			return;
		}
		float zoom = Math.max(0.05F, zoomArg);
		int cell = Math.max(1, cellPx);

		for (int ty = 0; ty < h; ty++) {
			for (int tx = 0; tx < w; tx++) {
				// 纹理坐标 → 画布地图像素
				int mapX = (int) Math.floor((tx - offX) / zoom);
				int mapY = (int) Math.floor((ty - offY) / zoom);
				int argb;

				if (mapX >= 0 && mapX < MapDrawProtocol.CANVAS_W && mapY >= 0 && mapY < MapDrawProtocol.CANVAS_H) {
					byte value = pixels[mapY * MapDrawProtocol.CANVAS_W + mapX];

					if (value == 0) {
						argb = checker
								? ((((tx / cell) + (ty / cell)) & 1) == 0 ? UiKit.CHECK_A : UiKit.CHECK_B)
								: background;
					} else {
						argb = MapPalette.argb(value);
					}
				} else {
					argb = 0;
				}

				image.setPixel(tx, ty, argb);
			}
		}

		this.texture.upload();
		this.cachedPixels = pixels.clone();
		this.cachedOffX = offX;
		this.cachedOffY = offY;
		this.cachedCw = cw;
		this.cachedCell = cellPx;
		this.cachedChecker = checker;
		this.cachedBackground = background;
		this.cachedW = w;
		this.cachedH = h;
	}
}