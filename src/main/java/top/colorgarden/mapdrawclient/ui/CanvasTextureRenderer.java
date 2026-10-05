package top.colorgarden.mapdrawclient.ui;

import java.util.Arrays;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 画布渲染：把「视口大小的画布内容」写进一张纹理，再 1:1 画出来。
 *
 * <p><b>26.x</b>：用新的 GpuTexture 体系（{@code createTexture → writeToTexture → blit(GpuTextureView, GpuSampler, …)}），
 * 老的 {@code DynamicTexture + blit(Identifier, …)} 在 26.x 的 GUI 管线里画不出来。</p>
 *
 * <p><b>≤1.21.x</b>：老式 {@code DynamicTexture + blit(Identifier, …)}（那套在旧版本有效）。</p>
 *
 * <p>纹理尺寸 = 绘制尺寸，1:1，不做子区域 UV；棋盘格与像素同一张图、同一套坐标。</p>
 */
public final class CanvasTextureRenderer {
	private static final int MAX_SIZE = 1024;

	//#if MC >= 260000
	private com.mojang.blaze3d.textures.GpuTexture gpuTexture;
	private com.mojang.blaze3d.textures.GpuTextureView gpuView;
	private com.mojang.blaze3d.textures.GpuSampler gpuSampler;
	//#else
	//$$ private net.minecraft.client.renderer.texture.DynamicTexture legacyTexture;
	//$$ private final Identifier legacyId = top.colorgarden.mapdrawclient.compat.Compat.makeId("mapdrawclient", "canvas_tex");
	//#endif

	private NativeImage image;
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

		//#if MC >= 260000
		g.blit(this.gpuView, this.gpuSampler, viewX, viewY, w, h, 0.0F, 0.0F, 1.0F, 1.0F);
		//#else
		//$$ g.blit(this.legacyId, viewX, viewY, 0, 0, w, h, w, h);
		//#endif
	}

	private void ensure(int w, int h) {
		if (this.image != null && this.texW == w && this.texH == h) {
			return;
		}

		this.release();
		this.image = new NativeImage(NativeImage.Format.RGBA, w, h, false);

		//#if MC >= 260000
		com.mojang.blaze3d.systems.GpuDevice device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
		this.gpuTexture = device.createTexture("mapdrawclient-canvas",
				com.mojang.blaze3d.textures.GpuTexture.USAGE_COPY_DST
						| com.mojang.blaze3d.textures.GpuTexture.USAGE_TEXTURE_BINDING,
				com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		this.gpuView = device.createTextureView(this.gpuTexture);
		this.gpuSampler = device.createSampler(
				com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
				com.mojang.blaze3d.textures.AddressMode.CLAMP_TO_EDGE,
				com.mojang.blaze3d.textures.FilterMode.NEAREST,
				com.mojang.blaze3d.textures.FilterMode.NEAREST, 1, java.util.OptionalDouble.empty());
		//#else
		//$$ this.legacyTexture = new net.minecraft.client.renderer.texture.DynamicTexture("mapdrawclient-canvas", w, h, false);
		//$$ top.colorgarden.mapdrawclient.compat.Compat.registerTexture(this.legacyId, this.legacyTexture);
		//#endif

		this.texW = w;
		this.texH = h;
		this.cachedPixels = null;
		this.cachedW = -1;
	}

	private void release() {
		//#if MC >= 260000
		if (this.gpuTexture != null) {
			this.gpuTexture.close();
		}

		this.gpuTexture = null;
		this.gpuView = null;
		//#else
		//$$ if (this.legacyTexture != null) {
		//$$ 	this.legacyTexture.close();
		//$$ }
		//$$
		//$$ this.legacyTexture = null;
		//#endif

		if (this.image != null) {
			this.image.close();
			this.image = null;
		}
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

		float zoom = Math.max(0.05F, zoomArg);
		int cell = Math.max(1, cellPx);

		for (int ty = 0; ty < h; ty++) {
			for (int tx = 0; tx < w; tx++) {
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

				this.image.setPixel(tx, ty, argb);
			}
		}

		//#if MC >= 260000
		com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
				.writeToTexture(this.gpuTexture, this.image);
		//#else
		//$$ this.image.copyTo(this.legacyTexture.getPixels());
		//$$ this.legacyTexture.upload();
		//#endif

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