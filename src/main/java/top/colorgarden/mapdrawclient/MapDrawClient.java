package top.colorgarden.mapdrawclient;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.input.BoardOpenHandler;
import top.colorgarden.mapdrawclient.input.MapDrawKeys;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.ui.UiKit;

/**
 * MapDraw 客户端 Mod 入口 (Fabric API)。
 *
 * <p>本模组只运行在物理客户端 (fabric.mod.json 中只有 client 入口)，
 * 通过插件消息通道 {@code mapdraw:main} 与 Paper 服务端的 MapDraw 插件通讯，
 * 提供原生画板 GUI 与全部开放端口的功能。</p>
 */
public class MapDrawClient implements ClientModInitializer {
	public static final String MOD_ID = "mapdrawclient";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		// 1) 配置（含界面主题）
		MapDrawConfig.load();
		UiKit.applyTheme(MapDrawConfig.get().lightTheme ? UiKit.Theme.LIGHT : UiKit.Theme.DARK);

		// 2) 网络：注册 mapdraw:main 的收发 (payload 编解码 + 全局接收器)
		MapDrawClientNetworking.init();

		// 3) 键位：只注册一个 J（打开控制台菜单），其余界面都从菜单里进
		MapDrawKeys.init();

		// 4) 手持画布地图 / 右键画布展示框 → 客户端二级菜单
		BoardOpenHandler.init();

		// 5) 画布缓存与延迟重同步
		CanvasStore.INSTANCE.init();

		LOGGER.info("[MapDrawClient] 初始化完成，通道 = mapdraw:main");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
