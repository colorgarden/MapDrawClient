package top.colorgarden.mapdrawclient.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 把一张 NativeImage 通过 MC 26.2 的 GpuTexture 管线贴到界面上。
 *
 * <p><b>双缓冲</b>：写入「下一张」纹理、绘制「当前」纹理，避免 MC 的命令编码器异步执行时
 * blit 读到还没写完的纹理（那会导致随机像素铺满画面）。</p>
 */
public final class CanvasImageTexture {
	private final GpuTexture[] textures = new GpuTexture[2];
	private final GpuTextureView[] views = new GpuTextureView[2];
	private com.mojang.blaze3d.textures.GpuSampler sampler;
	private int current = -1;
	private int width;
	private int height;
	private int frames;

	/** 上传新内容到「下一张」纹理，然后切换为当前（绘制时用上一张，保证读写不冲突）。 */
	public void upload(NativeImage image, int w, int h) {
		this.ensure(w, h);
		int next = this.current == 0 ? 1 : 0;

		try {
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.textures[next], image);
			this.current = next;

			if (++this.frames % 60 == 1) {
				top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info(
						"[MapDrawClient][画布GPU] 上传纹理 {}x{} → 缓冲 {}", w, h, next);
			}
		} catch (Throwable t) {
			top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.warn(
					"[MapDrawClient][画布GPU] 上传失败: {}", t.toString());
		}
	}

	/** 绘制当前纹理；没有可绘制的纹理时返回 false。 */
	public boolean draw(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		if (this.current < 0 || this.width <= 0) {
			return false;
		}

		try {
			g.blit(this.views[this.current], this.sampler, x, y, w, h, 0.0F, 0.0F, 1.0F, 1.0F);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private void ensure(int w, int h) {
		if (this.textures[0] != null && this.width == w && this.height == h) {
			return;
		}

		this.release();
		com.mojang.blaze3d.systems.GpuDevice device = RenderSystem.getDevice();

		for (int i = 0; i < 2; i++) {
			this.textures[i] = device.createTexture("mapdrawclient-canvas-" + i,
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					com.mojang.blaze3d.GpuFormat.RGBA8_UNORM, w, h, 1, 1);
			this.views[i] = device.createTextureView(this.textures[i]);
		}

		this.sampler = RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
		this.current = -1;
		this.width = w;
		this.height = h;
	}

	private void release() {
		for (int i = 0; i < 2; i++) {
			if (this.textures[i] != null) {
				this.textures[i].close();
				this.textures[i] = null;
			}

			this.views[i] = null;
		}

		this.current = -1;
	}
}