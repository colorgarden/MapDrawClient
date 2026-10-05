package top.colorgarden.mapdrawclient.ui;

import top.colorgarden.mapdrawclient.compat.Compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.MetaField;

/**
 * 画布元数据编辑：{@code 0x07 SET_META(String canvasId, byte field, String value)}。
 *
 * <p>field: 0=标题 1=描述 2=尺寸 3=防拷贝。</p>
 *
 * <p>尺寸用和「新建画布」一样的四档按钮（16/32/64/128），点档位只是选中，
 * 还要再点一次「确定提交尺寸」才会发 {@code 0x07}（二级确定，避免手滑改错分辨率）。</p>
 */
public class MetaScreen extends MapDrawScreen {
	/** 尺寸档位（逻辑分辨率）。 */
	private static final int[] SIZE_STOPS = {16, 32, 64, 128};

	private final String canvasId;

	private UiField titleField;
	private UiField descField;
	private UiButton sizeSubmitButton;
	private int pendingSize = -1;
	/** 最近一次提交的时间戳（0 = 还没提交过）。 */
	private long submitAt;

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

		this.panelW = Math.min(this.width - 16, 320);
		this.panelH = Math.min(this.height - 10, 200);
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

		// ---- 逻辑尺寸：四档按钮 + 二级确定 ----
		y += 30;
		this.pendingSize = canvas == null ? -1 : canvas.size();

		int sizeW = (inner - 6) / 4;

		for (int i = 0; i < SIZE_STOPS.length; i++) {
			final int value = SIZE_STOPS[i];
			UiButton button = this.addButton(x0 + i * (sizeW + 2), y, sizeW, 18,
					value + "x" + value, () -> this.pendingSize = value);
			button.selected = () -> this.pendingSize == value;
			button.tooltip = "选中 " + value + "x" + value + " 逻辑格（每格 "
					+ (128 / value) + "px），再点下面的「确定提交尺寸」才生效";
		}

		y += 22;
		this.sizeSubmitButton = this.addButton(x0, y, inner / 2 - 1, 18, UiIcon.CHECK, "确定提交尺寸", this::submitSize);
		this.sizeSubmitButton.enabled = () -> {
			CanvasData current = CanvasStore.INSTANCE.get(this.canvasId);
			return this.pendingSize > 0 && (current == null || current.size() != this.pendingSize);
		};
		this.sizeSubmitButton.tooltip = "发送 0x07 SET_META SIZE（已填色的画布服务端只允许放大）";

		y += 24;
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

		// 尺寸区说明
		int current = canvas == null ? -1 : canvas.size();
		String sizeInfo = "逻辑尺寸" + (current > 0 ? "（当前 " + current + "x" + current + "）" : "");
		Compat.text(g, this.font, sizeInfo, x0, this.sizeSubmitButton.y - 30, UiKit.TEXT_DIM, false);

		if (this.pendingSize > 0 && this.pendingSize != current) {
			String pending = "待提交 " + this.pendingSize + "x" + this.pendingSize
					+ "（每格 " + (128 / this.pendingSize) + "px）";
			Compat.text(g, this.font, pending, x0 + this.panelW - 20 - this.font.width(pending),
					this.sizeSubmitButton.y - 30, UiKit.ACCENT, false);
		}

		// 提交结果提示（正在提交 / 成功 / 失败）
		this.renderSubmitFeedback(g, x0, this.panelW - 20);

		if (canvas == null) {
			Compat.text(g, this.font, "尚未同步该画布 (ID: " + this.shortId(this.canvasId) + ")", x0,
					this.panelY + this.panelH - 12, UiKit.WARN, false);
			return;
		}

		int painted = this.paintedCount(canvas);
		Compat.text(g, this.font, painted > 0 ? "已填色：服务端只允许放大尺寸" : "空白画布：可自由放大 / 缩小",
				x0, this.panelY + this.panelH - 30, painted > 0 ? UiKit.WARN : UiKit.TEXT_MUTED, false);
		Compat.text(g, this.font, (canvas.isProtected() ? "已锁定保护" : "未保护")
				+ (canvas.animated() ? "  动图" : "")
				+ "  已落笔像素 " + painted, x0, this.panelY + this.panelH - 20,
				UiKit.TEXT_MUTED, false);
		Compat.text(g, this.font, "ID " + this.shortId(canvas.id()) + "  mapId=" + canvas.mapId()
				+ "  作者 " + this.safe(canvas.creator()), x0, this.panelY + this.panelH - 10,
				UiKit.TEXT_MUTED, false);
	}

	/** 提交结果提示：正在提交 / 成功 / 失败（读 CanvasStore 里最近一次 0x80 回执）。 */
	private void renderSubmitFeedback(GuiGraphicsExtractor g, int x0, int inner) {
		if (this.submitAt <= 0) {
			return;
		}

		CanvasStore store = CanvasStore.INSTANCE;
		long now = System.currentTimeMillis();
		String text;
		int color;

		if (store.lastResponseAt() >= this.submitAt
				&& store.lastResponsePacketId() == MapDrawProtocol.C2S_SET_META) {
			boolean ok = store.lastResponseSuccess();
			String detail = store.lastResponseMessage();
			text = (ok ? "✔ 提交成功" : "✘ 提交失败") + (detail.isEmpty() ? "" : "：" + detail);
			color = ok ? UiKit.OK : UiKit.ERR;
		} else if (now - this.submitAt < 3000) {
			text = "… 正在提交";
			color = UiKit.WARN;
		} else {
			text = "… 没有收到服务端回执";
			color = UiKit.TEXT_MUTED;
		}

		int maxW = Math.max(40, inner / 2 - 6);
		text = UiKit.ellipsize(this.font, text, maxW);
		Compat.text(g, this.font, text, x0 + inner - this.font.width(text), this.sizeSubmitButton.y + 5, color, false);
	}

	/** 二级确定：提交选中的尺寸。 */
	private void submitSize() {
		if (this.pendingSize <= 0) {
			CanvasStore.INSTANCE.setStatus("先选一个尺寸（16/32/64/128）", UiKit.WARN);
			return;
		}

		this.submit(MetaField.SIZE, String.valueOf(this.pendingSize));
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

		this.submitAt = System.currentTimeMillis();
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
