package top.colorgarden.mapdrawclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.MetaField;

/**
 * 画布元数据编辑：{@code 0x07 SET_META(String canvasId, byte field, String value)}。
 *
 * <p>field: 0=标题 1=描述 2=尺寸 3=防拷贝。</p>
 */
public class MetaScreen extends MapDrawScreen {
	private final String canvasId;

	private UiField titleField;
	private UiField descField;
	private UiField sizeField;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public MetaScreen(Screen parent, String canvasId) {
		super(Component.literal("画布元数据"));
		this.setParent(parent);
		this.canvasId = canvasId == null ? "" : canvasId;
	}

	@Override
	protected void init() {
		super.init();
		CanvasData canvas = CanvasStore.INSTANCE.get(this.canvasId);

		this.panelW = Math.min(this.width - 16, 300);
		this.panelH = Math.min(this.height - 10, 196);
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = (this.height - this.panelH) / 2;

		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;
		int y = this.panelY + 26;

		this.titleField = this.addField(x0, y, inner - 60, 18, "标题",
				canvas == null ? "" : canvas.title(), 64);
		this.addButton(x0 + inner - 58, y, 58, 18, "提交", () -> this.submit(MetaField.TITLE, this.titleField.value))
				.tooltip = "提交标题";

		y += 34;
		this.descField = this.addField(x0, y, inner - 60, 18, "描述",
				canvas == null ? "" : canvas.description(), 128);
		this.addButton(x0 + inner - 58, y, 58, 18, "提交", () -> this.submit(MetaField.DESCRIPTION, this.descField.value))
				.tooltip = "提交描述";

		y += 34;
		this.sizeField = this.addField(x0, y, inner - 60, 18, "可用尺寸 (16/32/64/128)",
				canvas == null ? "128" : String.valueOf(canvas.size()), 3);
		this.sizeField.digitsOnly = true;
		this.addButton(x0 + inner - 58, y, 58, 18, "提交", () -> this.submit(MetaField.SIZE, this.sizeField.value))
				.tooltip = "提交尺寸（填色后服务端拒绝缩小）";

		y += 26;
		UiButton noCopyButton = this.addButton(x0, y, inner / 2 - 1, 18, UiIcon.LOCK, "防拷贝", () -> {
			// 必须点击时现取状态：init 里捕获的快照可能还没同步过(null)，会导致永远发 true
			CanvasData current = CanvasStore.INSTANCE.get(this.canvasId);
			boolean now = current != null && current.noCopy();
			this.submit(MetaField.NO_COPY, now ? "false" : "true");
		});
		noCopyButton.tooltip = "切换防拷贝";
		noCopyButton.selected = () -> {
			CanvasData c = CanvasStore.INSTANCE.get(this.canvasId);
			return c != null && c.noCopy();
		};

		this.addButton(x0 + inner / 2 + 1, y, inner / 2 - 1, 18, "重新同步", () -> {
			MapDrawClientNetworking.requestCanvas(this.canvasId);
			CanvasStore.INSTANCE.setStatus("已请求画布数据", UiKit.TEXT);
		}).tooltip = "重新拉取画布数据";

		this.addButton(x0, y + 24, inner, 18, "返回画板", this::onClose);
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiKit.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
		UiKit.header(g, this.font, this.panelX + 4, this.panelY + 4, this.panelW - 8, "画布元数据");

		CanvasData canvas = CanvasStore.INSTANCE.get(this.canvasId);
		int x0 = this.panelX + 10;

		if (canvas == null) {
			g.text(this.font, "尚未同步该画布 (ID: " + this.shortId(this.canvasId) + ")", x0,
					this.panelY + this.panelH - 16, UiKit.WARN, false);
			return;
		}

		g.text(this.font, "ID " + this.shortId(canvas.id()) + "  mapId=" + canvas.mapId()
				+ "  作者 " + this.safe(canvas.creator()), x0, this.panelY + this.panelH - 16, UiKit.TEXT_MUTED, false);
		g.text(this.font, (canvas.isProtected() ? "已锁定保护" : "未保护")
				+ (canvas.animated() ? "  动图" : "")
				+ "  已落笔像素 " + this.paintedCount(canvas), x0, this.panelY + this.panelH - 26,
				UiKit.TEXT_MUTED, false);
	}

	private void submit(MetaField field, String value) {
		if (this.canvasId.isEmpty()) {
			CanvasStore.INSTANCE.setStatus("没有选中画布", UiKit.WARN);
			return;
		}

		String safe = value == null ? "" : value.trim();

		if (field == MetaField.SIZE) {
			int parsed;

			try {
				parsed = Integer.parseInt(safe);
			} catch (NumberFormatException e) {
				CanvasStore.INSTANCE.setStatus("尺寸必须是数字 (16/32/64/128)", UiKit.ERR);
				return;
			}

			if (parsed != 16 && parsed != 32 && parsed != 64 && parsed != 128) {
				CanvasStore.INSTANCE.setStatus("尺寸只能是 16/32/64/128", UiKit.ERR);
				return;
			}

			safe = String.valueOf(parsed);
		}

		if (field == MetaField.NO_COPY && !safe.equalsIgnoreCase("true") && !safe.equalsIgnoreCase("false")) {
			safe = "true";
		}

		MapDrawClientNetworking.setMeta(this.canvasId, field, safe);
		CanvasStore.INSTANCE.markStale(this.canvasId);
		CanvasStore.INSTANCE.setStatus("已提交 " + field.name() + " = " + safe + "", UiKit.OK);
	}

	private int paintedCount(CanvasData canvas) {
		int count = 0;

		for (byte b : canvas.pixels()) {
			if (b != 0) {
				count++;
			}
		}

		return count;
	}

	private String safe(String value) {
		return value == null || value.isEmpty() ? "-" : value;
	}

	private String shortId(String id) {
		if (id == null || id.isEmpty()) {
			return "-";
		}

		return id.length() <= 12 ? id : id.substring(0, 12) + "…";
	}
}
