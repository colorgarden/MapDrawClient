package top.colorgarden.mapdrawclient.ui;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.compat.Compat;

/**
 * 用 Minecraft 自己的 DynamicTexture 把一张 NativeImage 贴到界面上。
 *
 * <p>26.x / 1.21.11（新渲染体系）：带 RenderPipeline 的 blit；
 * 1.21.8 及更早（老体系）：经典 blit(ResourceLocation, x, y, blitOffset, u, v, w, h, texW, texH)。</p>
 */
public final class CanvasImageTexture {
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
				//$$ this.texture = new DynamicTexture(() -> "mapdrawclient-canvas", image);
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

	//#if MC >= 260000
	public boolean draw(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, int w, int h) {
	//#else
	//$$ public boolean draw(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h) {
	//#endif
		if (this.texture == null) {
			return false;
		}

		try {
			//#if MC >= 12108
			// 新渲染体系：必须带 RenderPipeline
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.id, x, y, 0.0F, 0.0F, w, h, w, h);
			//#else
			//$$ // 老渲染体系：经典 blit（blitOffset=0，UV 0,0，纹理尺寸=绘制尺寸 → 1:1）
			//$$ g.blit(net.minecraft.client.renderer.RenderType::guiTextured, this.id, x, y, 0.0F, 0.0F, w, h, w, h);
			//#endif
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
