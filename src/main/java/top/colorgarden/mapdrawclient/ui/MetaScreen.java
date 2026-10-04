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
 *
 * <p>尺寸用**离散滑块**（16/32/64/128 四档）而不是输入框：可选值就这四个，
 * 松手即提交，滑块下面标出四个档位，已经填过色的画布会提示「只允许放大」。</p>
 */
public class MetaScreen extends MapDrawScreen {
	/** 尺寸档位（逻辑分辨率）。 */
	private static final int[] SIZE_STOPS = {16, 32, 64, 128};

	private final String canvasId;

	private UiField titleField;
	private UiField descField;
	private int sizeSliderX;
	private int sizeSliderY;
	private int sizeSliderW;
	private int sizeSliderH = 12;
	private boolean sizeDragging;
	private int pendingSize = -1;

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

		// 尺寸：滑块（四档），松手即提交
		y += 34;
		this.sizeSliderX = x0;
		this.sizeSliderY = y;
		this.sizeSliderW = inner;
		this.pendingSize = canvas == null ? -1 : canvas.size();

		y += 30;
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
		if (this.sizeDragging) {
			this.pendingSize = this.sizeFromMouse(mouseX);
		}

		UiKit.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
		UiKit.header(g, this.font, this.panelX + 4, this.panelY + 4, this.panelW - 8, "画布元数据");

		CanvasData canvas = CanvasStore.INSTANCE.get(this.canvasId);
		int x0 = this.panelX + 10;

		// 尺寸滑块
		int current = this.pendingSize > 0 ? this.pendingSize
				: (canvas == null ? 128 : canvas.size());
		UiKit.slider(g, this.sizeSliderX, this.sizeSliderY, this.sizeSliderW, this.sizeSliderH,
				this.sizeRatio(current), UiKit.contains(this.sizeSliderX, this.sizeSliderY, this.sizeSliderW,
						this.sizeSliderH, mouseX, mouseY));

		// 档位刻度 + 数字
		for (int i = 0; i < SIZE_STOPS.length; i++) {
			int sx = this.sizeSliderX + Math.round(this.stopRatio(i) * (this.sizeSliderW - 4)) + 2;
			int sy = this.sizeSliderY + this.sizeSliderH;
			g.fill(sx, sy, sx + 1, sy + 4, UiKit.BORDER_HI);
			String label = String.valueOf(SIZE_STOPS[i]);
			boolean active = SIZE_STOPS[i] == current;
			g.text(this.font, label, sx - this.font.width(label) / 2, sy + 5,
					active ? UiKit.ACCENT : UiKit.TEXT_MUTED, false);
		}

		String sizeText = "逻辑尺寸 " + current + "x" + current + "（每格 " + (128 / Math.max(1, current)) + "px）";
		g.text(this.font, sizeText, this.sizeSliderX, this.sizeSliderY - 11, UiKit.TEXT_DIM, false);

		if (canvas == null) {
			g.text(this.font, "尚未同步该画布 (ID: " + this.shortId(this.canvasId) + ")", x0,
					this.panelY + this.panelH - 12, UiKit.WARN, false);
			return;
		}

		int painted = this.paintedCount(canvas);
		g.text(this.font, painted > 0 ? "已填色：服务端只允许放大尺寸" : "空白画布：可自由放大 / 缩小",
				x0, this.panelY + this.panelH - 30, painted > 0 ? UiKit.WARN : UiKit.TEXT_MUTED, false);
		g.text(this.font, (canvas.isProtected() ? "已锁定保护" : "未保护")
				+ (canvas.animated() ? "  动图" : "")
				+ "  已落笔像素 " + painted, x0, this.panelY + this.panelH - 20,
				UiKit.TEXT_MUTED, false);
		g.text(this.font, "ID " + this.shortId(canvas.id()) + "  mapId=" + canvas.mapId()
				+ "  作者 " + this.safe(canvas.creator()), x0, this.panelY + this.panelH - 10,
				UiKit.TEXT_MUTED, false);
	}

	// ------------------------------------------------------------------
	// 尺寸滑块
	// ------------------------------------------------------------------
	private float stopRatio(int index) {
		return (float) index / (SIZE_STOPS.length - 1);
	}

	private float sizeRatio(int size) {
		for (int i = 0; i < SIZE_STOPS.length; i++) {
			if (SIZE_STOPS[i] >= size) {
				return this.stopRatio(i);
			}
		}

		return 1.0F;
	}

	private int sizeFromMouse(int mouseX) {
		float ratio = UiKit.clamp01((mouseX - this.sizeSliderX - 2.0F) / Math.max(1, this.sizeSliderW - 4));
		int index = Math.round(ratio * (SIZE_STOPS.length - 1));
		return SIZE_STOPS[UiKit.clamp(index, 0, SIZE_STOPS.length - 1)];
	}

	@Override
	protected boolean onMouseClick(int x, int y, int button) {
		if (UiKit.contains(this.sizeSliderX, this.sizeSliderY - 4, this.sizeSliderW, this.sizeSliderH + 8, x, y)) {
			this.sizeDragging = true;
			this.pendingSize = this.sizeFromMouse(x);
			return true;
		}

		return false;
	}

	@Override
	protected boolean onMouseRelease(int x, int y, int button) {
		if (this.sizeDragging) {
			this.sizeDragging = false;
			CanvasData canvas = CanvasStore.INSTANCE.get(this.canvasId);

			if (this.pendingSize > 0 && (canvas == null || canvas.size() != this.pendingSize)) {
				this.submit(MetaField.SIZE, String.valueOf(this.pendingSize));
			}

			return true;
		}

		return false;
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
