package top.colorgarden.mapdrawclient.ui;

//#if MC >= 260000
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.compat.Compat;
//#endif

/**
 * 用 Minecraft 自己的 DynamicTexture 把一张 NativeImage 贴到界面上（仅 26.x 的 GPU 纹理管线）。
 *
 * <p>旧版本（1.20.6 ~ 1.21.11）没有 GpuTextureView/SamplerCache，画布走逐像素路径，本类是空实现。</p>
 */
public final class CanvasImageTexture {
	//#if MC >= 260000
	private final Identifier id = Compat.makeId("mapdrawclient", "canvas_dynamic_tex");
	private DynamicTexture texture;
	private int width;
	private int height;

	/** 换一张新图（MC 的类自己管纹理与采样器）。 */
	public void upload(NativeImage image, int w, int h) {
		try {
			if (this.texture == null || this.width != w || this.height != h) {
				if (this.texture != null) {
					this.texture.close();
				}

				this.texture = new DynamicTexture(() -> "mapdrawclient-canvas", image);
				net.minecraft.client.Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
				this.width = w;
				this.height = h;
			} else {
				this.texture.setPixels(image);
				this.texture.upload();
			}
		} catch (Throwable t) {
			top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.warn("[MapDrawClient][画布GPU] 上传失败: {}", t.toString());
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
	//#else
	//$$ public void upload(com.mojang.blaze3d.platform.NativeImage image, int w, int h) {
	//$$ 	// 旧版本走逐像素路径
	//$$ }
	//$$
	//#endif
}
