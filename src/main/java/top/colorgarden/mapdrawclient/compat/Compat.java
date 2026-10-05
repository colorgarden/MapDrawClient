package top.colorgarden.mapdrawclient.compat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import top.colorgarden.mapdrawclient.net.MapDrawPayload;

/**
 * 跨版本兼容层（preprocessor 的 {@code //#if} 指令）。
 *
 * <p>主版本是 26.2：文件按 26.2 的 API 写，往旧版本编译时 preprocessor 会删掉不匹配的分支
 * （旧版本分支用 {@code //$$} 前缀，在主版本里是注释）。类名差异由
 * {@code versions/mapping-*.txt} 的类映射处理（例如 {@code GuiGraphicsExtractor → GuiGraphics}）。</p>
 *
 * <p>规则：<b>业务代码不要直接调用这些原版 API</b>，一律走这里。</p>
 */
public final class Compat {
	private Compat() {
	}

	// ------------------------------------------------------------------
	// 界面
	// ------------------------------------------------------------------
	/** 打开/关闭界面：26.2 起挪到了 {@code Minecraft.gui} 上。 */
	public static void setScreen(Minecraft client, Screen screen) {
		//#if MC >= 260200
		client.gui.setScreen(screen);
		//#else
		//$$ client.setScreen(screen);
		//#endif
	}

	/** 取当前界面（旧版本里 {@code screen} 是字段，不是方法）。 */
	public static Screen screen(Minecraft client) {
		//#if MC >= 260200
		return client.gui.screen();
		//#else
		//$$ return client.screen;
		//#endif
	}

	// ------------------------------------------------------------------
	// 绘制
	// ------------------------------------------------------------------
	/** 文字：26.2 叫 {@code text}，旧版本叫 {@code drawString}。 */
	public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, boolean shadow) {
		//#if MC >= 260102
		g.text(font, text, x, y, color, shadow);
		//#else
		//$$ g.drawString(font, text, x, y, color, shadow);
		//#endif
	}

	/** 描边：26.2 叫 {@code outline}，旧版本没有这个 API，自己用 4 条线画。 */
	public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		//#if MC >= 260102
		g.outline(x, y, w, h, color);
		//#else
		//$$ g.hLine(x, x + w - 1, y, color);
		//$$ g.hLine(x, x + w - 1, y + h - 1, color);
		//$$ g.vLine(x, y, y + h - 1, color);
		//$$ g.vLine(x + w - 1, y, y + h - 1, color);
		//#endif
	}

	/** 竖线：26.2 叫 {@code verticalLine}，旧版本叫 {@code vLine}。 */
	public static void verticalLine(GuiGraphicsExtractor g, int x, int y1, int y2, int color) {
		//#if MC >= 260102
		g.verticalLine(x, y1, y2, color);
		//#else
		//$$ g.vLine(x, y1, y2, color);
		//#endif
	}

	/** 横线：26.2 叫 {@code horizontalLine}，旧版本叫 {@code hLine}。 */
	public static void horizontalLine(GuiGraphicsExtractor g, int x1, int x2, int y, int color) {
		//#if MC >= 260102
		g.horizontalLine(x1, x2, y, color);
		//#else
		//$$ g.hLine(x1, x2, y, color);
		//#endif
	}

	// ------------------------------------------------------------------
	// 其它
	// ------------------------------------------------------------------
	/** 给玩家发一条客户端消息。 */
	public static void sendMessage(Player player, Component message) {
		//#if MC >= 260102
		player.sendSystemMessage(message);
		//#else
		//$$ player.displayClientMessage(message, false);
		//#endif
	}

	/** 注册按键绑定（Fabric API：26.x 是 KeyMappingHelper，旧版是 KeyBindingHelper）。 */
	public static KeyMapping registerKeyMapping(KeyMapping mapping) {
		//#if MC >= 260102
		return net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
		//#else
		//$$ return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(mapping);
		//#endif
	}

	/** 注册客户端→服务端的自定义负载类型（Fabric API 在 26.x 改了方法名）。 */
	public static void registerPayloadC2S() {
		//#if MC >= 260102
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay()
				.register(MapDrawPayload.TYPE, MapDrawPayload.CODEC);
		//#else
		//$$ net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
		//$$ 		.register(MapDrawPayload.TYPE, MapDrawPayload.CODEC);
		//#endif
	}

	/** 注册服务端→客户端的自定义负载类型。 */
	public static void registerPayloadS2C() {
		//#if MC >= 260102
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay()
				.register(MapDrawPayload.TYPE, MapDrawPayload.CODEC);
		//#else
		//$$ net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
		//$$ 		.register(MapDrawPayload.TYPE, MapDrawPayload.CODEC);
		//#endif
	}

	// ------------------------------------------------------------------
	// NBT
	// ------------------------------------------------------------------
	/** 读 NBT 字符串：26.2 起返回 Optional，旧版本返回可空 String。 */
	public static String nbtString(net.minecraft.nbt.CompoundTag tag, String key) {
		//#if MC >= 12105
		return tag.getString(key).orElse(null);
		//#else
		//$$ return tag.contains(key) ? tag.getString(key) : null;
		//#endif
	}

	/** 读 NBT 子 compound：26.2 起返回 Optional，旧版本直接返回（缺失时是空 compound）。 */
	public static net.minecraft.nbt.CompoundTag nbtCompound(net.minecraft.nbt.CompoundTag tag, String key) {
		//#if MC >= 12105
		return tag.getCompound(key).orElse(null);
		//#else
		//$$ return tag.getCompound(key);
		//#endif
	}
	/** 构造资源 ID：1.21 起是 Identifier.fromNamespaceAndPath，1.20.6 是 new ResourceLocation。 */
	public static net.minecraft.resources.Identifier makeId(String namespace, String path) {
		//#if MC >= 12100
		return net.minecraft.resources.Identifier.fromNamespaceAndPath(namespace, path);
		//#else
		//$$ return new net.minecraft.resources.ResourceLocation(namespace, path);
		//#endif
	}
	// ------------------------------------------------------------------
	// 纹理（画板 GPU 渲染用）
	// ------------------------------------------------------------------
	/** 把一张纹理按缩放 blit 到界面（26.2 / 1.21.x 都要带 RenderPipeline）。 */
	public static void blit(GuiGraphicsExtractor g, net.minecraft.resources.Identifier texture,
			int x, int y, float u, float v, int w, int h, int texW, int texH) {
		//#if MC >= 12108
		g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, texW, texH);
		//#else
		//$$ // 1.21.5 以下走 CPU 逐像素绘制，CanvasTexture 不参与编译，这里用不到
		//#endif
	}

	/** 往 NativeImage 写一个 ARGB 像素（旧版本 setPixel 要的是 ABGR，这里统一换算）。 */
	public static void setPixel(com.mojang.blaze3d.platform.NativeImage image, int x, int y, int argb) {
		//#if MC >= 12108
		image.setPixel(x, y, argb);
		//#else
		//$$ // 同上：旧版本不编译 CanvasTexture，无需实现
		//#endif
	}

	/** 注册动态纹理。 */
	public static void registerTexture(net.minecraft.resources.Identifier id,
			net.minecraft.client.renderer.texture.AbstractTexture texture) {
		//#if MC >= 12100
		net.minecraft.client.Minecraft.getInstance().getTextureManager().register(id, texture);
		//#else
		//$$ // 同上：旧版本不编译 CanvasTexture，无需实现
		//#endif
	}
	/** 把纹理的 (0,0)-(u1,v1) 子矩形画到 (x, y, w, h)（Skija 画布渲染用）。 */
	public static void blitSub(GuiGraphicsExtractor g, net.minecraft.resources.Identifier texture,
			int x, int y, int w, int h, float u1, float v1) {
		//#if MC >= 12108
		g.blit(texture, x, y, w, h, 0.0F, 0.0F, u1, v1);
		//#else
		//$$ // 1.21.8 以下没有该重载（SkiaCanvasRenderer 也不参与编译）
		//#endif
	}

}