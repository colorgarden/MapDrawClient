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

	/** 描边：26.2 叫 {@code outline}，旧版本叫 {@code renderOutline}。 */
	public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		//#if MC >= 260102
		g.outline(x, y, w, h, color);
		//#else
		//$$ g.renderOutline(x, y, w, h, color);
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
}
