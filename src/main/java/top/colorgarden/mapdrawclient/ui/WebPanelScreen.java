package top.colorgarden.mapdrawclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.compat.Compat;
import top.colorgarden.mapdrawclient.ui.web.WebPanel;

/**
 * 网页界面测试：用 Graphene 把打包的 HTML 显示出来（第 2 步验证用）。
 *
 * <p>面板几何由本界面自己算（{@code MapDrawScreen} 只提供 buttons/fields 与输入分发）。</p>
 */
public class WebPanelScreen extends MapDrawScreen {
	private int px;
	private int py;
	private int pw;
	private int ph;
	private io.github.trethore.graphene.fabric.api.widget.GrapheneWebViewWidget web;

	public WebPanelScreen() {
		super(Component.literal("MapDraw 网页界面"));
	}

	@Override
	protected void init() {
		super.init();
		// 不要全屏暗色遮罩，否则会把网页压暗
		this.drawScrim = false;
		this.pw = Math.min(this.width - 20, 420);
		this.ph = Math.min(this.height - 20, 320);
		this.px = (this.width - this.pw) / 2;
		this.py = (this.height - this.ph) / 2;

		int inner = this.pw - 20;
		int webH = this.ph - 56;
		this.web = WebPanel.create(this, this.px + 10, this.py + 30, inner, webH, "web/panel.html");

		if (this.web != null) {
			// 文档：GrapheneWebViewWidget 自己处理渲染/焦点/输入/光标/缩放/生命周期 → 交给 Screen 的控件列表
			this.addRenderableWidget(this.web);
			this.web.setFocused(true);
		}

		if (this.web != null) {
			// 注册 Java 端桥接：JS 点按钮 → 改配置/触发操作 → 状态推回页面
			WebPanel.wire(this.web, (channel, payload) -> this.onPanelEvent(channel, payload));
		}

		this.addButton(this.px + 10, this.py + this.ph - 22, inner, 16, "返回", this::onClose);
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiKit.panel(g, this.px, this.py, this.pw, this.ph);
		UiKit.header(g, this.font, this.px + 4, this.py + 4, this.pw - 8, "Graphene 网页界面（测试）");

		if (this.web == null) {
			Compat.text(g, this.font, "网页不可用：Graphene 未加载或初始化失败（看日志）",
					this.px + 10, this.py + 40, UiKit.ERR, false);
			return;
		}

		// 渲染由 addRenderableWidget 负责（不要手动 render）
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
		if (this.web != null && this.web.mouseClicked(event, doubleClick)) {
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
		if (this.web != null && this.web.mouseReleased(event)) {
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.web != null && this.web.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (this.web != null && this.web.keyPressed(event)) {
			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (this.web != null && this.web.charTyped(event)) {
			return true;
		}

		return super.charTyped(event);
	}

	/** 最后再画一次网页（renderOverlay 在所有控件之后执行），确保不被任何暗色层压暗。 */
	@Override
	protected void renderOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (this.web == null) {
			return;
		}

		try {
			io.github.trethore.graphene.fabric.api.surface.BrowserGuiSurface surface = this.web.surface();

			if (surface != null) {
				surface.render(g, this.px + 10, this.py + 30, this.pw - 20, this.ph - 56);
			}
		} catch (Throwable ignored) {
			// 忽略
		}
	}

	/** 处理来自网页的事件（按 Graphene 教程用 onEvent 收，payload 是 JSON 字符串）。 */
	private void onPanelEvent(String channel, String payload) {
		MapDrawConfig cfg = MapDrawConfig.get();
		String status = "网页事件: " + channel + " " + (payload == null ? "" : payload);

		switch (channel) {
			case "mapdraw:tool" -> {
				if (payload != null) {
					if (payload.contains("ERASER")) {
						cfg.tool = 1;
					} else if (payload.contains("PAINTBUCKET")) {
						cfg.tool = 2;
					} else if (payload.contains("NONE")) {
						cfg.tool = 3;
					} else {
						cfg.tool = 0;
					}

					MapDrawConfig.save();
				}
			}
			case "mapdraw:brush" -> {
				int size = this.numberIn(payload, 1);

				if (size > 0) {
					cfg.brushSize = size;
					MapDrawConfig.save();
				}
			}
			case "mapdraw:color" -> {
				int index = this.numberIn(payload, -1);

				if (index >= 0) {
					cfg.color = index & 0xFF;
					MapDrawConfig.save();
				}
			}
			case "mapdraw:action" -> {
				String id = top.colorgarden.mapdrawclient.canvas.CanvasStore.INSTANCE.currentId();

				if (payload != null && !id.isEmpty()) {
					if (payload.contains("undo")) {
						top.colorgarden.mapdrawclient.net.MapDrawClientNetworking.undo(id);
					} else if (payload.contains("redo")) {
						top.colorgarden.mapdrawclient.net.MapDrawClientNetworking.redo(id);
					} else if (payload.contains("protect")) {
						top.colorgarden.mapdrawclient.net.MapDrawClientNetworking.protect(id);
					} else if (payload.contains("sync")) {
						// markStale 会让 CanvasStore 在下一 tick 重新拉取该画布
						top.colorgarden.mapdrawclient.canvas.CanvasStore.INSTANCE.markStale(id);
					}
				}
			}
			case "mapdraw:ready" -> status = "网页已连接";
			default -> {
			}
		}

		top.colorgarden.mapdrawclient.canvas.CanvasStore.INSTANCE.setStatus(status, 0xFF55FF55);

		if (this.web != null) {
			WebPanel.pushState(this.web, new WebPanel.PanelState(
					cfg.tool == 1 ? "ERASER" : cfg.tool == 2 ? "PAINTBUCKET" : cfg.tool == 3 ? "NONE" : "PEN",
					cfg.color & 0xFF, cfg.brushSize, status));
		}
	}

	/** 从 JSON 片段里抠出第一个数字（够用即可）。 */
	private int numberIn(String json, int fallback) {
		if (json == null) {
			return fallback;
		}

		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(-?\\d+)").matcher(json);
		return m.find() ? Integer.parseInt(m.group(1)) : fallback;
	}

	@Override
	public void onClose() {
		if (this.web != null) {
			try {
				this.web.close();
			} catch (Throwable ignored) {
				// 忽略
			}

			this.web = null;
		}

		super.onClose();
	}
}