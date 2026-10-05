package top.colorgarden.mapdrawclient.ui;

import top.colorgarden.mapdrawclient.compat.Compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.canvas.HeldMapProbe;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;

/**
 * 画布列表：挑本会话已同步的画布，或从手持地图的 PDC 直接识别。
 *
 * <p>画布 ID 是服务端生成的 UUID，客户端<b>只读显示、不允许编辑</b>——
 * 手输一个不存在的 UUID 没有意义，能操作哪张画布完全由服务端决定。</p>
 */
public class CanvasListScreen extends MapDrawScreen {
	private static final int ROW_H = 18;

	private int scroll;
	private int listX;
	private int listY;
	private int listW;
	private int listH;
	private int visibleRows = 8;
	private final List<String> rowIds = new ArrayList<>();

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public CanvasListScreen(Screen parent) {
		super(Component.literal("画布列表"));
		this.setParent(parent);
	}

	@Override
	protected void init() {
		super.init();

		this.panelW = Math.min(this.width - 12, 330);
		this.panelH = Math.min(this.height - 12, 210);
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = (this.height - this.panelH) / 2;

		int x0 = this.panelX + 8;
		int inner = this.panelW - 16;

		this.listX = x0;
		this.listY = this.panelY + 34;
		this.listW = inner;
		this.listH = Math.max(ROW_H * 2, this.panelH - 34 - 80);
		this.visibleRows = Math.max(2, this.listH / ROW_H);

		this.addButton(x0, this.panelY + this.panelH - 62, inner, 18, UiIcon.CHECK, "从手持地图读取 (PDC)",
				this::readHeld).tooltip = "读取主手/背包画布地图 PDC 里的 mapdraw:canvas_id";

		this.addButton(x0, this.panelY + this.panelH - 22, inner / 2 - 1, 18, UiIcon.SYNC, "全部刷新",
				this::refreshAll).tooltip = "对缓存中的所有画布各发一次 0x0C";
		this.addButton(x0 + inner / 2 + 1, this.panelY + this.panelH - 22, inner / 2 - 1, 18, "返回", this::onClose);
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiKit.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
		UiKit.header(g, this.font, this.panelX + 4, this.panelY + 4, this.panelW - 8,
				"画布列表 — 本会话已同步 " + CanvasStore.INSTANCE.all().size() + " 张");

		Compat.text(g, this.font, "点击列表即可切换；画布 ID 由服务端生成，这里只读显示",
				this.panelX + 8, this.panelY + 20, UiKit.TEXT_MUTED, false);

		// 当前画布 ID（只读）
		String currentId = CanvasStore.INSTANCE.currentId();
		Compat.text(g, this.font, "当前画布 ID: " + (currentId.isEmpty() ? "(未选择)" : currentId),
				this.panelX + 8, this.panelY + this.panelH - 74, UiKit.TEXT_DIM, false);

		g.fill(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH, UiKit.SLOT);
		Compat.outline(g, this.listX, this.listY, this.listW, this.listH, UiKit.BORDER);

		this.rowIds.clear();

		for (CanvasData canvas : CanvasStore.INSTANCE.all()) {
			this.rowIds.add(canvas.id());
		}

		if (this.rowIds.isEmpty()) {
			Compat.text(g, this.font, "缓存为空：输入画布 ID 后点「请求」即可拉取。",
					this.listX + 6, this.listY + 8, UiKit.WARN, false);
			return;
		}

		this.scroll = UiKit.clamp(this.scroll, 0, Math.max(0, this.rowIds.size() - this.visibleRows));

		for (int row = 0; row < this.visibleRows && row + this.scroll < this.rowIds.size(); row++) {
			String id = this.rowIds.get(row + this.scroll);
			CanvasData canvas = CanvasStore.INSTANCE.get(id);
			int ry = this.listY + 2 + row * ROW_H;
			boolean hovered = UiKit.contains(this.listX, this.listY, this.listW, this.listH, mouseX, mouseY)
					&& UiKit.contains(this.listX, ry, this.listW, ROW_H - 1, mouseX, mouseY);
			boolean current = id.equals(CanvasStore.INSTANCE.currentId());
			g.fill(this.listX + 2, ry, this.listX + this.listW - 2, ry + ROW_H - 2,
					current ? 0xFF2E4A56 : (hovered ? UiKit.BTN_HOVER : UiKit.BTN));

			String name = canvas == null ? "?" : canvas.displayName();
			String meta = canvas == null ? "" : ("  " + canvas.size() + "x" + canvas.size()
					+ (canvas.isProtected() ? "  保护" : "")
					+ (canvas.animated() ? "  动图" : "")
					+ (canvas.noCopy() ? "  防拷贝" : ""));
			Compat.text(g, this.font, UiKit.ellipsize(this.font, name + meta, this.listW - 110), this.listX + 6, ry + 5,
					UiKit.TEXT, false);
			Compat.text(g, this.font, shortId(id), this.listX + this.listW - 100, ry + 5, UiKit.TEXT_MUTED, false);
		}

		if (this.rowIds.size() > this.visibleRows) {
			Compat.text(g, this.font, (this.scroll + 1) + "-" + Math.min(this.scroll + this.visibleRows, this.rowIds.size())
					+ " / " + this.rowIds.size(), this.listX + this.listW - 60, this.listY - 10, UiKit.TEXT_MUTED, false);
		}
	}

	@Override
	protected boolean onMouseClick(int x, int y, int button) {
		if (!UiKit.contains(this.listX, this.listY, this.listW, this.listH, x, y)) {
			return false;
		}

		int row = (y - this.listY - 2) / ROW_H;

		if (row < 0 || row + this.scroll >= this.rowIds.size()) {
			return false;
		}

		this.select(this.rowIds.get(row + this.scroll));
		return true;
	}

	@Override
	protected boolean onMouseScroll(int x, int y, double amount) {
		if (UiKit.contains(this.listX, this.listY, this.listW, this.listH, x, y)) {
			this.scroll += amount > 0 ? -1 : 1;
			return true;
		}

		return false;
	}

	@Override
	protected void onFieldSubmit(UiField field) {
		// 本界面没有可编辑字段（画布 ID 只读）
	}

	/** 从手持地图的 PDC 读出 ID 并直接切换过去。 */
	private void readHeld() {
		HeldMapProbe.ProbeResult held = HeldMapProbe.fromHands();

		if (held == null) {
			CanvasStore.INSTANCE.setStatus("主手/背包里没找到 MapDraw 画布地图", UiKit.WARN);
			return;
		}

		CanvasStore.INSTANCE.setStatus("已从画布地图读出 ID: " + held.canvasId(), UiKit.OK);
		this.select(held.canvasId());
	}

	private void select(String id) {
		CanvasStore.INSTANCE.setCurrent(id);
		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.lastCanvasId = id;
		MapDrawConfig.save();
		MapDrawClientNetworking.requestCanvas(id);
		CanvasStore.INSTANCE.setStatus("当前画布: " + shortId(id), UiKit.OK);

		if (this.parent instanceof BoardScreen board) {
			board.setCanvasId(id);
			this.onClose();
		} else {
			this.open(new BoardScreen(id));
		}
	}

	private void refreshAll() {
		if (CanvasStore.INSTANCE.isEmpty()) {
			CanvasStore.INSTANCE.setStatus("缓存为空，无可刷新项", UiKit.WARN);
			return;
		}

		for (CanvasData canvas : CanvasStore.INSTANCE.all()) {
			MapDrawClientNetworking.requestCanvas(canvas.id());
		}

		CanvasStore.INSTANCE.setStatus("已刷新 " + CanvasStore.INSTANCE.all().size() + " 张画布", UiKit.TEXT);
	}

	private String shortId(String id) {
		if (id == null || id.isEmpty()) {
			return "-";
		}

		return id.length() <= 10 ? id : id.substring(0, 10) + "…";
	}
}
