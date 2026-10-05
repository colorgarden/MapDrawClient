package top.colorgarden.mapdrawclient.ui.web;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import io.github.trethore.graphene.api.Graphene;
import io.github.trethore.graphene.api.GrapheneContext;
import io.github.trethore.graphene.fabric.api.surface.BrowserView;
import io.github.trethore.graphene.fabric.api.widget.GrapheneWebViewWidget;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * Graphene（JCEF/Chromium）网页面板：把打包在 mod 里的 HTML 当成游戏内控件显示。
 *
 * <p>用法：{@code GrapheneWebViewWidget widget = WebPanel.create(screen, x, y, w, h, "web/panel.html");}
 * 然后把它加进界面的控件列表（本模组的 {@code MapDrawScreen} 用 addButton 管理控件，
 * 所以这里额外提供 {@link #tick()} 之类不需要，直接由界面持有即可）。</p>
 */
public final class WebPanel {
	private static GrapheneContext context;
	private static boolean failed;

	private WebPanel() {
	}

	/** 取得（并首次注册）Graphene 上下文；失败返回 null 并只记一次日志。 */
	public static GrapheneContext context() {
		if (context != null || failed) {
			return context;
		}

		try {
			context = Graphene.register(MapDrawClient.MOD_ID);
			MapDrawClient.LOGGER.info("[MapDrawClient] Graphene 已注册，context id = {}", context.id());
		} catch (Throwable t) {
			failed = true;
			MapDrawClient.LOGGER.warn("[MapDrawClient] Graphene 注册失败（网页界面不可用）: {}", t.toString());
		}

		return context;
	}

	/** 创建网页控件；失败返回 null。 */
	public static GrapheneWebViewWidget create(Screen screen, int x, int y, int w, int h, String assetPath) {
		GrapheneContext ctx = context();

		if (ctx == null) {
			return null;
		}

		try {
			// 文档：打包资源用 appAssets()，默认命名空间就是本模组 id
			String url = ctx.appAssets().url(assetPath);
			BrowserView view = BrowserView.builder(ctx).url(url).build();
			GrapheneWebViewWidget widget = new GrapheneWebViewWidget(screen, x, y, w, h,
					Component.literal("MapDraw Web"), view);
			MapDrawClient.LOGGER.info("[MapDrawClient] 网页面板已创建: {} ({}x{})", url, w, h);
			scheduleReload(widget, url);
			return widget;
		} catch (Throwable t) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] 创建网页面板失败: {}", t.toString());
			return null;
		}
	}
	/** 状态消息（Java → JS）。 */
	public record PanelState(String tool, int color, int brush, String status) {
	}

	/**
	 * 注册 Java 端桥接：收 JS 的 tool/brush/color/action 事件，并把当前状态推回页面。
	 *
	 * <p>按 Graphene 官方教程：{@code bridge.onEvent(channel, (ch, json) -> ...)} +
	 * {@code bridge.emitJson(channel, payload)}。</p>
	 */
	public static void wire(GrapheneWebViewWidget widget, java.util.function.BiConsumer<String, String> onEvent) {
		if (widget == null) {
			return;
		}

		try {
			io.github.trethore.graphene.api.bridge.GrapheneBridge bridge = widget.bridge();

			for (String channel : new String[]{"mapdraw:tool", "mapdraw:brush", "mapdraw:color", "mapdraw:action", "mapdraw:ready"}) {
				bridge.onEvent(channel, (ch, payload) -> onEvent.accept(ch, payload));
			}

			MapDrawClient.LOGGER.info("[MapDrawClient] 网页面板桥接已注册");
		} catch (Throwable t) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] 网页面板桥接注册失败: {}", t.toString());
		}
	}

	/** 把状态推给页面（JS 侧 bridge.on("mapdraw:state")）。 */
	public static void pushState(GrapheneWebViewWidget widget, PanelState state) {
		if (widget == null) {
			return;
		}

		try {
			widget.bridge().emitJson("mapdraw:state", state);
		} catch (Throwable ignored) {
			// 忽略
		}
	}
	/** 资源索引可能晚于界面初始化 → 首次可能 404，延迟 reload 两次兜底。 */
	private static void scheduleReload(GrapheneWebViewWidget widget, String url) {
		Thread thread = new Thread(() -> {
			for (long delay : new long[]{1000L, 3000L}) {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					return;
				}

				try {
					net.minecraft.client.Minecraft.getInstance().execute(() -> {
						try {
							if (!widget.currentUrl().equals(url)) {
								widget.navigate(url);
							} else {
								widget.reload();
							}
						} catch (Throwable ignored) {
							// 忽略
						}
					});
				} catch (Throwable ignored) {
					// 忽略
				}
			}
		}, "MapDrawClient-WebReload");
		thread.setDaemon(true);
		thread.start();
	}
}