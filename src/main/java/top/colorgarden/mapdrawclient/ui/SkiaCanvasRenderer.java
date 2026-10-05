package top.colorgarden.mapdrawclient.ui;

import top.colorgarden.mapdrawclient.canvas.CanvasData;

/**
 * 已废弃：GPU/Skija 画布渲染的占位桩。
 *
 * <p>那些实验（Skija 直画帧缓冲、MC 纹理、GpuTexture）都已放弃，配置项 {@code skiaCanvas}
 * 默认 false，因此本类不会被调用；保留它只是为了不动 BoardScreen 的分支代码。</p>
 */
public final class SkiaCanvasRenderer {
	public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, CanvasData canvas,
			int viewX, int viewY, int viewW, int viewH, int originX, int originY, int cw,
			int cellPx, boolean checker, int background) {
		// 什么都不做
	}
}