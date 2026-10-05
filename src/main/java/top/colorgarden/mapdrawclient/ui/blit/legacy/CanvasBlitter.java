package top.colorgarden.mapdrawclient.ui.blit;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** 画布贴图的 blit：1.21.1 及更早：ResourceLocation 打头的 9 参老签名（按版本编译期三选一，同一类名，其余文件在构建时排除）。 */
public final class CanvasBlitter {
	private CanvasBlitter() {
	}

	public static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y,
			int size, int texW, int texH) {
		g.blit(texture, x, y, size, size, 0.0F, 0.0F, 1.0F, 1.0F);
	}
}