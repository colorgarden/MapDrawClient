package top.colorgarden.mapdrawclient.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.compat.Compat;

/**
 * 把一张 NativeImage 通过 MC 26.2 的 GpuTexture 管线贴到界面上。
 *
 * <p>配方来自 Graphene（在 26.2 上验证可行）：createTexture → createTextureView →
 * 包成 AbstractTexture 并注册到 TextureManager → blit(view, sampler, …)。</p>
 *
 * <p>纹理尺寸 = 绘制尺寸，1:1，无子区域 UV。</p>
 */
public final class CanvasImageTexture {
	private final Identifier id = Compat.makeId("mapdrawclient", "canvas_gpu_tex");
	private GpuTexture texture;
	private GpuTextureView view;
	private com.mojang.blaze3d.textures.GpuSampler sampler;
	private AbstractTexture wrapped;
	private int frames;
	private int width;
	private int height;

	/** 把 image 贴到 (x, y, w, h)；失败时返回 false。 */
	public boolean draw(GuiGraphicsExtractor g, NativeImage image, int x, int y, int w, int h) {
		if (image == null || w <= 0 || h <= 0) {
			return false;
		}

		try {
			this.ensure(w, h);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture, image);

			if (++this.frames % 60 == 1) {
				top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info(
						"[MapDrawClient][画布GPU] 纹理 {}x{} → blit 到 ({}, {}) {}x{}  (第 {} 帧)",
						this.width, this.height, x, y, w, h, this.frames);
			}
			g.blit(this.view, this.sampler, x, y, w, h, 0.0F, 0.0F, 1.0F, 1.0F);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private void ensure(int w, int h) {
		if (this.texture != null && this.width == w && this.height == h) {
			return;
		}

		this.release();
		com.mojang.blaze3d.systems.GpuDevice device = RenderSystem.getDevice();
		this.texture = device.createTexture("mapdrawclient-canvas-nanovg",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
				com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		this.view = device.createTextureView(this.texture);
		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
		this.wrapped = new WrappedTexture(this.texture, this.view);
		net.minecraft.client.Minecraft.getInstance().getTextureManager().register(this.id, this.wrapped);
		this.width = w;
		this.height = h;
	}

	private void release() {
		if (this.texture != null) {
			this.texture.close();
			this.texture = null;
		}

		this.view = null;
	}

	/** GUI 管线要求纹理在 TextureManager 里。 */
	private static final class WrappedTexture extends AbstractTexture {
		private final GpuTexture texture;
		private final GpuTextureView view;

		WrappedTexture(GpuTexture texture, GpuTextureView view) {
			this.texture = texture;
			this.view = view;
		}

		@Override
		public GpuTexture getTexture() {
			return this.texture;
		}

		@Override
		public GpuTextureView getTextureView() {
			return this.view;
		}
	}
}