package top.colorgarden.mapdrawclient.ui;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.compat.Compat;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;
import top.colorgarden.mapdrawclient.ui.blit.CanvasBlitter;

/**
 * 画布渲染缓存：把「屏幕上要显示的画布」（棋盘格 + 像素）按**屏幕像素**写进一张动态纹理，
 * 每帧一次 1:1 blit。
 *
 * <p>关键：纹理尺寸 = 屏幕上的画布边长，内容也按屏幕像素采样 —— 棋盘格和像素内容来自
 * 同一张图、同一套坐标，永远不会错位；缩放由「重建纹理」完成，不做纹理采样缩放，
 * 所以也不会有采样抖动。</p>
 *
 * <p>只有内容/尺寸变化时才重建 + 上传；缩放动画期间每帧重建一次（几百×几百像素，可忽略）。</p>
 */
public final class CanvasTexture {
	/** 纹理边长上限（超过就退回采样缩放，避免极端缩放时显存暴涨）。 */
	private static final int MAX_TEX = 2048;

	private final Identifier id = Compat.makeId("mapdrawclient", "canvas_preview");
	private DynamicTexture texture;
	private int texSize = -1;
	private byte[] cachedPixels;
	private int cachedSize = -1;
	private int cachedCell = -1;
	private boolean cachedChecker;
	private int cachedBackground = -1;

	/** 画整张画布到 (x, y)，边长 size（屏幕像素）。 */
	public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, CanvasData canvas,
			int x, int y, int size, int cellPx, boolean checker, int background) {
		if (size <= 0) {
			return;
		}

		this.ensure(Math.min(size, MAX_TEX));
		this.update(canvas, size, cellPx, checker, background);
		CanvasBlitter.blit(g, this.id, x, y, size, MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H);
	}

	private void ensure(int wanted) {
		if (this.texture != null && this.texSize == wanted) {
			return;
		}

		if (this.texture != null) {
			this.texture.close();
		}

		this.texture = new DynamicTexture("mapdrawclient-canvas", wanted, wanted, false);
		this.texSize = wanted;
		this.cachedPixels = null;
		this.cachedSize = -1;
		Compat.registerTexture(this.id, this.texture);
	}

	private void update(CanvasData canvas, int size, int cellPx, boolean checker, int background) {
		byte[] pixels = canvas.pixels();
		boolean same = this.cachedSize == size
				&& this.cachedCell == cellPx
				&& this.cachedChecker == checker
				&& this.cachedBackground == background
				&& this.cachedPixels != null
				&& java.util.Arrays.equals(this.cachedPixels, pixels);

		if (same) {
			return;
		}

		NativeImage image = this.texture.getPixels();
		int tex = this.texSize;
		int cell = Math.max(1, Math.round(cellPx * (float) tex / size));

		for (int ty = 0; ty < tex; ty++) {
			// 屏幕像素 → 地图像素（最近邻）
			int my = (int) ((long) ty * MapDrawProtocol.CANVAS_H / tex);

			for (int tx = 0; tx < tex; tx++) {
				int mx = (int) ((long) tx * MapDrawProtocol.CANVAS_W / tex);
				byte value = pixels[my * MapDrawProtocol.CANVAS_W + mx];
				int argb;

				if (value == 0) {
					argb = checker
							? ((((tx / cell) + (ty / cell)) & 1) == 0 ? UiKit.CHECK_A : UiKit.CHECK_B)
							: background;
				} else {
					argb = MapPalette.argb(value);
				}

				image.setPixel(tx, ty, argb);
			}
		}

		this.texture.upload();
		this.cachedPixels = pixels.clone();
		this.cachedSize = size;
		this.cachedCell = cellPx;
		this.cachedChecker = checker;
		this.cachedBackground = background;
	}
}