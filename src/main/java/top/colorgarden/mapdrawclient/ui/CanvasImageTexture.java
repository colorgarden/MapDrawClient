package top.colorgarden.mapdrawclient.ui;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import top.colorgarden.mapdrawclient.compat.Compat;

/**
 * 用 Minecraft 自己的 DynamicTexture 把一张 NativeImage 贴到界面上（三档版本分支）。
 *
 * <p>1.21.8+ / 26.x：blit(RenderPipeline, id, …)；
 * 1.21.5~1.21.7：blit(RenderType::guiTextured, id, …)；
 * 1.21.4 及更早：blit(RenderType::gui, id, …)。</p>
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

				//#if MC >= 12105
				this.texture = new DynamicTexture(() -> "mapdrawclient-canvas", image);
				//#else
				//$$ this.texture = new DynamicTexture(image);
				//#endif
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
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, this.id, x, y, 0.0F, 0.0F, w, h, w, h);
			//#elseif MC >= 12105
			//$$ g.blit(net.minecraft.client.renderer.RenderType::guiTextured, this.id, x, y, 0.0F, 0.0F, w, h, w, h);
			//#elseif MC >= 12102
			//$$ g.blit(rl -> net.minecraft.client.renderer.RenderType.gui(), this.id, x, y, 0.0F, 0.0F, w, h, w, h);
			//#else
			//$$ // 1.21.1 / 1.20.6：经典 9 参 blit(id, x, y, u, v, w, h, texW, texH)
			//$$ g.blit(this.id, x, y, 0.0F, 0.0F, w, h, w, h);
			//#endif
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
	/** 关闭并释放纹理（画板关闭时调用，避免残留纹理被别的界面误用）。 */
	public void release() {
		if (this.texture != null) {
			try {
				this.texture.close();
			} catch (Throwable ignored) {
				// 忽略
			}

			this.texture = null;
		}

		this.width = 0;
		this.height = 0;
	}
}