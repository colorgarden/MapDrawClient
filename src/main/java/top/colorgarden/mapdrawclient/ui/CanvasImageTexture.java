package top.colorgarden.mapdrawclient.ui;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.compat.Compat;

/**
 * 用 Minecraft 自己的 {@link DynamicTexture} 把一张 NativeImage 贴到界面上。
 *
 * <p>照 MC 自身的实现（javap 确认）：DynamicTexture 内部就是
 * {@code RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, pixels)}，
 * 采样器与纹理视图由它自己管理（{@code getTextureView()} / {@code getSampler()}），
 * 我们只负责「换图 → upload → blit」。</p>
 */
public final class CanvasImageTexture {
	private final Identifier id = Compat.makeId("mapdrawclient", "canvas_dynamic_tex");
	private DynamicTexture texture;
	private int width;
	private int height;
	private com.mojang.blaze3d.textures.GpuSampler clampSampler;

	/** 换一张新图（会新建 DynamicTexture；MC 的类自己管纹理与采样器）。 */
	public void upload(NativeImage image, int w, int h) {
		try {
			if (this.texture == null || this.width != w || this.height != h) {
				if (this.texture != null) {
					this.texture.close();
				}

				// 这个构造会自己 createTexture + upload，并接管 image 的所有权
				this.texture = new DynamicTexture(() -> "mapdrawclient-canvas", image);
				net.minecraft.client.Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
				this.width = w;
				this.height = h;
				this.clampSampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
						.getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST);
			} else {
				this.texture.setPixels(image);
				this.texture.upload();
			}

		} catch (Throwable t) {
			top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.warn(
					"[MapDrawClient][画布GPU] 上传失败: {}", t.toString());
		}
	}

	public boolean draw(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		if (this.texture == null) {
			return false;
		}

		try {
			// 必须带 RenderPipeline（不带 pipeline 的 Identifier 版在 26.2 完全不渲染）
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.id, x, y, 0.0F, 0.0F, w, h, w, h);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}