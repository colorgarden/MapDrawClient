package top.colorgarden.mapdrawclient.ui;

import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.compat.Compat;
import top.colorgarden.mapdrawclient.ui.blit.CanvasBlitter;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 画布的 GPU 渲染缓存：把整张 128x128 画布（棋盘格 + 像素）烘焙进一张动态纹理，
 * 每帧只 blit 一次。
 *
 * <p>之前是每帧成千上万次 {@code fill()}，fill 调用本身的开销把帧时间吃掉了；
 * 现在只有内容变化时才重写像素并上传纹理（{@code upload()}），其余帧零成本。</p>
 */
public final class CanvasTexture {
	private final Identifier id = Compat.makeId("mapdrawclient", "canvas_preview");
	private DynamicTexture texture;
	private byte[] cachedPixels;
	private int cachedCell = -1;
	private boolean cachedChecker;
	private int cachedBackground = -1;

	/** 画整张画布到 (x, y)，边长 size（GUI 像素）。 */
	public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, CanvasData canvas,
			int x, int y, int size, int cell, boolean checker, int background) {
		this.ensure();
		this.update(canvas, cell, checker, background);
		CanvasBlitter.blit(g, this.id, x, y, size, MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H);
	}

	private void ensure() {
		if (this.texture != null) {
			return;
		}

		//#if MC >= 12105
		this.texture = new DynamicTexture("mapdrawclient-canvas",
				MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H, false);
		//#else
		//$$ this.texture = new DynamicTexture(MapDrawProtocol.CANVAS_W, MapDrawProtocol.CANVAS_H, false);
		//#endif
		Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
	}

	private void update(CanvasData canvas, int cell, boolean checker, int background) {
		byte[] pixels = canvas.pixels();
		boolean same = this.cachedPixels != null
				&& this.cachedCell == cell
				&& this.cachedChecker == checker
				&& this.cachedBackground == background
				&& Arrays.equals(this.cachedPixels, pixels);

		if (same) {
			return;
		}

		NativeImage image = this.texture.getPixels();
		int cellSize = Math.max(1, cell);

		for (int py = 0; py < MapDrawProtocol.CANVAS_H; py++) {
			int rowBase = py * MapDrawProtocol.CANVAS_W;

			for (int px = 0; px < MapDrawProtocol.CANVAS_W; px++) {
				byte value = pixels[rowBase + px];
				int argb;

				if (value == 0) {
					argb = checker
							? ((((px / cellSize) + (py / cellSize)) & 1) == 0 ? UiKit.CHECK_A : UiKit.CHECK_B)
							: background;
				} else {
					argb = MapPalette.argb(value);
				}

				//#if MC >= 12105
				image.setPixel(px, py, argb);
				//#else
				//$$ image.fillRect(px, py, 1, 1, argb);
				//#endif
			}
		}

		this.texture.upload();
		this.cachedPixels = pixels.clone();
		this.cachedCell = cell;
		this.cachedChecker = checker;
		this.cachedBackground = background;
	}
}