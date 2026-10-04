package top.colorgarden.mapdrawclient.ui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.canvas.HeldMapProbe;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.GuiType;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.ToolType;

/**
 * 画板主界面：显示 128x128 地图像素、直接落笔、批量画线、缩放/平移、调色板与全部端口操作。
 *
 * <p>端口覆盖：
 * 0x01 单点 / 0x02 批量 / 0x03 撤销 / 0x04 重做 / 0x05 保护 / 0x06 解锁 /
 * 0x07 元数据 / 0x08 新建 / 0x09 工具 / 0x0A 颜色 / 0x0B 服务器界面 / 0x0C 请求画布。</p>
 */
public class BoardScreen extends MapDrawScreen {
	private static final int[] ZOOM_LEVELS = {1, 2, 3, 4, 6, 8, 12, 16, 24, 32};
	private static final int SWATCH_COLS = 9;
	private static final int SWATCH_CELL = 11;
	private static final int SWATCH_GAP = 1;

	private final String initialCanvasId;
	private String canvasId = "";

	private ToolType tool = ToolType.PEN;
	private byte color = 114;
	private int zoom = 1;
	private int panX;
	private int panY;
	private boolean showGrid = true;
	private boolean autoFit = true;

	private int panelX;
	private int panelW = 116;
	private int viewX;
	private int viewY;
	private int viewW;
	private int viewH;
	private int originX;
	private int originY;

	private int colorBarX;
	private int colorBarY;
	private int colorBarW;
	private int colorBarH;
	private int swatchX0;
	private int swatchY0;

	// 笔画状态
	private boolean dragging;
	private boolean strokeFlushed;
	private boolean tempEraser;
	private boolean panning;
	private int panStartX;
	private int panStartY;
	private int panOriginX;
	private int panOriginY;
	private final List<int[]> strokePoints = new ArrayList<>();
	private int lastCx = -1;
	private int lastCy = -1;
	private boolean hasLast;
	private boolean requestedOnOpen;

	// 悬停
	private int hoverCx = -1;
	private int hoverCy = -1;

	public BoardScreen(String canvasId) {
		super(Component.literal("MapDraw 画板"));
		this.initialCanvasId = canvasId == null ? "" : canvasId;
	}

	// ------------------------------------------------------------------
	// 布局
	// ------------------------------------------------------------------
	@Override
	protected void init() {
		super.init();
		MapDrawConfig cfg = MapDrawConfig.get();

		this.canvasId = this.initialCanvasId.isEmpty() ? CanvasStore.INSTANCE.currentId() : this.initialCanvasId;

		// 还没指定画布时，先看主手是不是插件生成的地图画 (PDC 里有 mapdraw:canvas_id)
		if (this.canvasId.isEmpty()) {
			HeldMapProbe.ProbeResult held = HeldMapProbe.fromMainHand();

			if (held != null) {
				this.canvasId = held.canvasId();
				CanvasStore.INSTANCE.setCurrent(this.canvasId);
				CanvasStore.INSTANCE.setStatus("已从手持地图识别画布: "
						+ (held.title().isEmpty() ? held.canvasId() : held.title()), UiKit.OK);
			}
		}
		this.tool = ToolType.byId(cfg.tool);
		this.color = (byte) cfg.color;
		this.zoom = UiKit.clamp(cfg.zoom, 1, 32);
		this.autoFit = cfg.autoFit;
		this.showGrid = cfg.showGrid;

		this.panelW = 116;
		this.panelX = this.width - this.panelW - 4;
		this.viewX = 4;
		this.viewY = 24;
		this.viewW = Math.max(60, this.panelX - this.viewX - 4);
		this.viewH = Math.max(60, this.height - this.viewY - 22);

		int x0 = this.panelX + 4;
		int inner = this.panelW - 8;
		int halfW = (inner - 2) / 2;
		int quarterW = (inner - 6) / 4;

		// 顶栏图标按钮 (右对齐)
		int bx = this.width - 20;

		this.addButton(bx, 3, 16, 16, UiIcon.CLOSE, "", this::onClose).tooltip = "关闭 (Esc)";
		bx -= 18;
		this.addButton(bx, 3, 16, 16, UiIcon.PLUS, "", () -> this.setZoomIndex(this.zoomIndex() + 1)).tooltip = "放大 (+)";
		bx -= 18;
		this.addButton(bx, 3, 16, 16, UiIcon.MINUS, "", () -> this.setZoomIndex(this.zoomIndex() - 1)).tooltip = "缩小 (-)";
		bx -= 18;
		this.addButton(bx, 3, 16, 16, UiIcon.CHECK, "", this::enableAutoFit).tooltip = "缩放自动适配画布（16x16 也能铺满）";
		bx -= 18;

		UiButton gridButton = this.addButton(bx, 3, 16, 16, UiIcon.GRID, "", this::toggleGrid);
		gridButton.tooltip = "显示/隐藏像素网格 (G)";
		gridButton.selected = () -> this.showGrid;
		bx -= 18;

		this.addButton(bx, 3, 16, 16, UiIcon.SYNC, "", this::sendSync).tooltip = "重新拉取画布数据 (Ctrl+S)";
		bx -= 18;
		this.addButton(bx, 3, 16, 16, UiIcon.MENU, "", () -> this.sendServerGui(GuiType.MENU))
				.tooltip = "打开服务端菜单";
		bx -= 18;
		this.addButton(bx, 3, 16, 16, UiIcon.LIST, "", () -> this.open(new CanvasListScreen(this)))
				.tooltip = "画布列表 / 输入画布 ID (L)";

		// ---- 右面板：工具 ----
		int y = 24;

		UiButton pen = this.addButton(x0, y + 10, quarterW, 16, UiIcon.PEN, "笔", () -> this.setTool(ToolType.PEN));
		pen.selected = () -> this.tool == ToolType.PEN;
		pen.tooltip = "画笔：单击点绘 / 拖拽画线 (1)";

		UiButton eraser = this.addButton(x0 + quarterW + 2, y + 10, quarterW, 16, UiIcon.ERASER, "擦",
				() -> this.setTool(ToolType.ERASER));
		eraser.selected = () -> this.tool == ToolType.ERASER;
		eraser.tooltip = "橡皮擦：擦成透明 (2)";

		UiButton bucket = this.addButton(x0 + (quarterW + 2) * 2, y + 10, quarterW, 16, UiIcon.BUCKET, "桶",
				() -> this.setTool(ToolType.PAINTBUCKET));
		bucket.selected = () -> this.tool == ToolType.PAINTBUCKET;
		bucket.tooltip = "油漆桶：泛洪填充连续区域 (3)";

		UiButton none = this.addButton(x0 + (quarterW + 2) * 3, y + 10, quarterW, 16, UiIcon.NONE, "无",
				() -> this.setTool(ToolType.NONE));
		none.selected = () -> this.tool == ToolType.NONE;
		none.tooltip = "清空手持工具 (4)";

		// ---- 右面板：颜色 ----
		y = 64;
		this.colorBarX = x0;
		this.colorBarY = y;
		this.colorBarW = inner;
		this.colorBarH = 14;

		this.addButton(x0, y + 16, inner, 14, UiIcon.PALETTE, "16 色调色板", () -> this.open(new PaletteScreen(this)))
				.tooltip = "打开调色板：16 色 / RGB 滑块 / HEX 输入";

		this.swatchX0 = x0;
		this.swatchY0 = y + 32;

		// ---- 右面板：操作 ----
		y = 136;
		this.addButton(x0, y, halfW, 16, UiIcon.UNDO, "撤销", this::sendUndo).tooltip = "撤销 (Ctrl+Z)";
		this.addButton(x0 + halfW + 2, y, halfW, 16, UiIcon.REDO, "重做", this::sendRedo).tooltip = "重做 (Ctrl+Y)";

		y += 18;
		UiButton protectButton = this.addButton(x0, y, halfW, 16, UiIcon.LOCK, "保护", this::toggleProtect);
		protectButton.tooltip = "锁定 / 解除保护 (K)";
		protectButton.selected = () -> {
			CanvasData canvas = this.canvas();
			return canvas != null && canvas.isProtected();
		};

		this.addButton(x0 + halfW + 2, y, halfW, 16, UiIcon.SYNC, "同步", this::sendSync)
				.tooltip = "重新拉取画布数据";

		y += 18;
		this.addButton(x0, y, halfW, 16, UiIcon.LIST, "元数据", () -> this.open(new MetaScreen(this, this.canvasId)))
				.tooltip = "标题 / 描述 / 尺寸 / 防拷贝";
		this.addButton(x0 + halfW + 2, y, halfW, 16, UiIcon.NEW, "新建", () -> this.open(new CreateCanvasScreen(this)))
				.tooltip = "新建一张画布";

		y += 18;
		this.addButton(x0, y, inner, 16, UiIcon.CHECK, "读手持地图 (H)", this::readHeldMap)
				.tooltip = "从主手画布地图的 PDC (mapdraw:canvas_id) 直接识别画布并同步";

		// 打开画板时自动拉一次画布数据 (0x0C)；窗口 resize 会重新 init，这里只发一次
		if (cfg.requestOnOpen && !this.canvasId.isEmpty() && !this.requestedOnOpen) {
			this.requestedOnOpen = true;
			MapDrawClientNetworking.requestCanvas(this.canvasId);
		}
	}

	// ------------------------------------------------------------------
	// 渲染
	// ------------------------------------------------------------------
	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		CanvasData canvas = this.canvas();
		CanvasStore store = CanvasStore.INSTANCE;

		// 采样拖拽 (只要鼠标按下且有位移，就补一条像素直线)
		if (this.dragging) {
			this.sampleDrag(mouseX, mouseY);
		}

		// 中键拖动平移
		if (this.panning) {
			this.panX = this.panOriginX + (mouseX - this.panStartX);
			this.panY = this.panOriginY + (mouseY - this.panStartY);
		}

		// ---- 顶栏 ----
		g.fill(0, 0, this.width, 20, UiKit.HEADER);
		g.fill(0, 20, this.width, 21, UiKit.BORDER);
		String title = "MapDraw 画板";
		g.text(this.font, title, 6, 6, UiKit.ACCENT, false);
		int titleW = this.font.width(title) + 8;

		if (canvas != null) {
			String info = canvas.displayName() + "  " + canvas.logicalSize() + "x" + canvas.logicalSize()
					+ " 逻辑格 (每格 " + canvas.gridN() + "px)"
					+ (canvas.isProtected() ? "  §c已保护" : "")
					+ (canvas.animated() ? "  §b动图" : "")
					+ "  #" + this.shortId(this.canvasId);
			g.text(this.font, UiKit.ellipsize(this.font, info, this.panelX - titleW - 140), 6 + titleW, 6,
					UiKit.TEXT_DIM, false);
		} else {
			g.text(this.font, "未选择画布 — 按 L 打开画布列表或输入画布 ID", 6 + titleW, 6, UiKit.WARN, false);
		}

		if (!MapDrawClientNetworking.canSend()) {
			g.text(this.font, "通道不可用", this.panelX - 74, 6, UiKit.ERR, false);
		}

		// ---- 画布视口 ----
		// 自动适配：整张画布永远铺满整个画布视口。
		// 注意这里是 128（地图像素边），不是 canvas.size()——size 只是逻辑网格分辨率
		// （16x16 的画布底层仍然是整张 128x128，一个逻辑像素 = 8x8 个地图像素）。
		if (this.autoFit) {
			int fit = Math.min(this.viewW, this.viewH) / MapDrawProtocol.CANVAS_W;
			this.zoom = UiKit.clamp(fit, 1, 32);
		}

		int cw = MapDrawProtocol.CANVAS_W * this.zoom;
		int baseX = this.viewX + (this.viewW - cw) / 2;
		int baseY = this.viewY + (this.viewH - cw) / 2;
		int maxPanX = Math.max(0, (cw - this.viewW) / 2);
		int maxPanY = Math.max(0, (cw - this.viewH) / 2);
		this.panX = UiKit.clamp(this.panX, -maxPanX, maxPanX);
		this.panY = UiKit.clamp(this.panY, -maxPanY, maxPanY);
		this.originX = baseX + this.panX;
		this.originY = baseY + this.panY;

		g.fill(this.viewX, this.viewY, this.viewX + this.viewW, this.viewY + this.viewH, UiKit.VIEWPORT);
		g.outline(this.viewX, this.viewY, this.viewW, this.viewH, UiKit.BORDER);

		g.enableScissor(this.viewX + 1, this.viewY + 1, this.viewX + this.viewW - 1, this.viewY + this.viewH - 1);

		if (canvas == null) {
			UiKit.checkerboard(g, this.originX, this.originY, cw, cw, Math.max(2, this.zoom * 2));
		} else {
			// 先用「锚定在画布坐标上的棋盘格」铺底，再画像素；
			// 这样图案只跟画布坐标有关，绝不会随笔画内容移动
			this.renderCanvasBackground(g, cw);
			this.renderPixels(g, canvas, cw);
		}

		// 网格按「逻辑格」画：一格 = gridN x gridN 个地图像素（size=16 时是 8x8 一格），
		// 整张 128x128 都是可画区，所以网格铺满整张画布。
		if (this.showGrid && canvas != null) {
			int gridN = canvas.gridN();
			int step = gridN * Math.max(1, this.zoom);
			int canvasEnd = MapDrawProtocol.CANVAS_W * this.zoom;

			if (step >= 3) {
				for (int px = 0; px <= MapDrawProtocol.CANVAS_W; px += gridN) {
					int x = this.originX + px * this.zoom;
					int y = this.originY + px * this.zoom;
					int c = ((px / gridN) % 8 == 0) ? UiKit.GRID_MAJOR : UiKit.GRID_MINOR;

					if (x >= this.viewX && x <= this.viewX + this.viewW) {
						g.verticalLine(x, this.originY, this.originY + canvasEnd, c);
					}

					if (y >= this.viewY && y <= this.viewY + this.viewH) {
						g.horizontalLine(this.originX, this.originX + canvasEnd, y, c);
					}
				}
			}
		}

		// 可用网格边界由 renderPixels 一起处理（压暗 + 描边），这里不再重复画

		// 悬停预览
		this.hoverCx = this.toCanvasX(mouseX);
		this.hoverCy = this.toCanvasY(mouseY);

		if (canvas != null && this.inViewport(mouseX, mouseY) && this.hoverCx >= 0 && this.hoverCy >= 0) {
			// 预览块按逻辑格吸附：服务端一格就是 gridN x gridN 个地图像素
			int cell = canvas.gridN() * Math.max(1, this.zoom);
			int px = this.originX + canvas.snapX(this.hoverCx) * this.zoom;
			int py = this.originY + canvas.snapY(this.hoverCy) * this.zoom;
			ToolType preview = this.activeTool();

			if (preview == ToolType.ERASER) {
				g.fill(px, py, px + cell, py + cell, 0x80FF5555);
			} else if (preview != ToolType.NONE) {
				g.fill(px, py, px + cell, py + cell,
						0xC0000000 | MapPalette.rgb(this.activeColor()));
			}

			g.outline(px - 1, py - 1, cell + 2, cell + 2, UiKit.HOVER_OUTLINE);
		}

		g.disableScissor();

		// ---- 右面板装饰文字 ----
		g.fill(this.panelX, 24, this.panelX + this.panelW, this.height - 22, UiKit.PANEL);
		g.outline(this.panelX, 24, this.panelW, this.height - 46, UiKit.BORDER);

		int x0 = this.panelX + 4;
		int inner = this.panelW - 8;

		UiKit.header(g, this.font, x0, 24, inner, "工具");
		UiKit.header(g, this.font, x0, 54, inner, "颜色");

		// 当前颜色条
		UiKit.swatch(g, this.colorBarX + 1, this.colorBarY + 1, this.colorBarH - 2, this.color, false,
				UiKit.contains(this.colorBarX, this.colorBarY, this.colorBarW, this.colorBarH, this.mouseX, this.mouseY));
		g.outline(this.colorBarX, this.colorBarY, this.colorBarW, this.colorBarH, UiKit.BORDER);
		g.text(this.font, MapPalette.name(this.color) + " #" + (this.color & 0xFF),
				this.colorBarX + this.colorBarH + 3, this.colorBarY + 3, UiKit.TEXT, false);

		// 快捷色
		byte[] quick = this.quickColors();

		for (int i = 0; i < quick.length; i++) {
			int sx = this.swatchX0 + (i % SWATCH_COLS) * (SWATCH_CELL + SWATCH_GAP);
			int sy = this.swatchY0 + (i / SWATCH_COLS) * (SWATCH_CELL + SWATCH_GAP);
			boolean hovered = UiKit.contains(sx, sy, SWATCH_CELL, SWATCH_CELL, this.mouseX, this.mouseY);
			UiKit.swatch(g, sx, sy, SWATCH_CELL, quick[i], quick[i] == this.color, hovered);
		}

		UiKit.header(g, this.font, x0, 126, inner, "操作");

		// ---- 底栏 ----
		g.fill(0, this.height - 20, this.width, this.height, UiKit.HEADER);

		String bottom;

		if (canvas != null && this.hoverCx >= 0 && this.hoverCy >= 0 && this.inViewport(mouseX, mouseY)) {
			byte pixel = canvas.pixel(canvas.snapX(this.hoverCx), canvas.snapY(this.hoverCy));
			bottom = "逻辑格 " + canvas.logicalX(this.hoverCx) + "," + canvas.logicalY(this.hoverCy)
					+ "  (" + canvas.logicalSize() + "x" + canvas.logicalSize()
					+ ", 每格 " + canvas.gridN() + "px)"
					+ "  |  该处像素 " + MapPalette.name(pixel)
					+ "  |  缩放 x" + this.zoom + (this.autoFit ? " (自动)" : "");
		} else {
			bottom = "左键画 · 右键按住=临时橡皮 · 中键拖动=平移 · 1/2/3/4 切工具 · Ctrl+Z 撤销 · G 网格 · 滚轮缩放";
		}

		// 先给右侧状态消息留出空间，避免两段文字叠在一起
		String status = CanvasStore.INSTANCE.status();
		int statusMax = Math.max(60, this.width / 2);
		status = UiKit.ellipsize(this.font, status, statusMax);
		int statusW = this.font.width(status);
		int hintMax = Math.max(40, this.width - statusW - 20);
		int hintW = this.font.width(bottom);

		if (hintW > hintMax) {
			bottom = UiKit.ellipsize(this.font, bottom, hintMax);
		}

		g.text(this.font, bottom, 6, this.height - 14, UiKit.TEXT_DIM, false);
		g.text(this.font, status, this.width - 6 - statusW, this.height - 14,
				CanvasStore.INSTANCE.statusFresh(4000) ? CanvasStore.INSTANCE.statusColor() : UiKit.TEXT_MUTED, false);
	}

	/**
	 * 画布底：透明区域的棋盘格。
	 *
	 * <p><b>相位由画布坐标决定</b>（而不是由「这一行透明像素从哪开始」决定），
	 * 所以擦掉/画上像素都不会让图案位移——之前的实现就是锚在像素段起点上，才会"随像素动"。</p>
	 */
	private void renderCanvasBackground(GuiGraphicsExtractor g, int cw) {
		int cell = Math.max(2, Math.min(this.zoom, 16));
		int x0 = Math.max(this.originX, this.viewX + 1);
		int y0 = Math.max(this.originY, this.viewY + 1);
		int x1 = Math.min(this.originX + cw, this.viewX + this.viewW - 1);
		int y1 = Math.min(this.originY + cw, this.viewY + this.viewH - 1);

		if (x1 <= x0 || y1 <= y0) {
			return;
		}

		if (!MapDrawConfig.get().showCheckerboard) {
			g.fill(x0, y0, x1, y1, UiKit.VIEWPORT);
			return;
		}

		int firstCellX = Math.floorDiv(x0 - this.originX, cell);
		int firstCellY = Math.floorDiv(y0 - this.originY, cell);
		int lastCellX = Math.floorDiv(x1 - this.originX, cell);
		int lastCellY = Math.floorDiv(y1 - this.originY, cell);

		// 格子太多就退化成纯色，避免每帧上万个 fill
		if ((long) (lastCellX - firstCellX + 1) * (lastCellY - firstCellY + 1) > 4096) {
			g.fill(x0, y0, x1, y1, UiKit.CHECK_B);
			return;
		}

		for (int cy = firstCellY; cy <= lastCellY; cy++) {
			int py = this.originY + cy * cell;

			for (int cx = firstCellX; cx <= lastCellX; cx++) {
				int px = this.originX + cx * cell;
				boolean light = (((cx + cy) & 1) == 0);
				g.fill(Math.max(px, x0), Math.max(py, y0),
						Math.min(px + cell, x1), Math.min(py + cell, y1),
						light ? UiKit.CHECK_A : UiKit.CHECK_B);
			}
		}
	}

	/**
	 * 画整张 128x128 地图像素；透明像素留给底下的棋盘格。
	 *
	 * <p>之前这里只画 {@code size x size} 个像素，等于把 16x16 画布当成「只有左上角
	 * 16x16 像素」，于是画板既没铺满、又只能画出几个巨大的色块。</p>
	 */
	private void renderPixels(GuiGraphicsExtractor g, CanvasData canvas, int cw) {
		byte[] pixels = canvas.pixels();
		int viewRight = this.viewX + this.viewW;
		int viewBottom = this.viewY + this.viewH;
		int zoom = Math.max(1, this.zoom);

		for (int y = 0; y < MapDrawProtocol.CANVAS_H; y++) {
			int sy = this.originY + y * zoom;

			if (sy + zoom < this.viewY || sy > viewBottom) {
				continue;
			}

			int rowBase = y * MapDrawProtocol.CANVAS_W;
			int x = 0;

			while (x < MapDrawProtocol.CANVAS_W) {
				byte value = pixels[rowBase + x];
				int start = x;
				x++;
				int end = x;

				while (x < MapDrawProtocol.CANVAS_W && pixels[rowBase + x] == value) {
					x++;
					end = x;
				}

				if (value == 0) {
					continue;
				}

				int sx = this.originX + start * zoom;
				int ex = this.originX + end * zoom;

				if (ex < this.viewX || sx > viewRight) {
					continue;
				}

				g.fill(sx, sy, ex, sy + zoom, MapPalette.argb(value));
			}
		}
	}

	// ------------------------------------------------------------------
	// 输入
	// ------------------------------------------------------------------
	@Override
	protected boolean onMouseClick(int x, int y, int button) {
		// 颜色条
		if (UiKit.contains(this.colorBarX, this.colorBarY, this.colorBarW, this.colorBarH, x, y)) {
			this.open(new PaletteScreen(this));
			return true;
		}

		// 快捷色
		byte[] quick = this.quickColors();

		for (int i = 0; i < quick.length; i++) {
			int sx = this.swatchX0 + (i % SWATCH_COLS) * (SWATCH_CELL + SWATCH_GAP);
			int sy = this.swatchY0 + (i / SWATCH_COLS) * (SWATCH_CELL + SWATCH_GAP);

			if (UiKit.contains(sx, sy, SWATCH_CELL, SWATCH_CELL, x, y)) {
				this.applyColor(quick[i]);
				return true;
			}
		}

		// 画布：左键=当前工具，右键按住=临时橡皮，中键=拖动平移
		if (this.inViewport(x, y) && button == 2) {
			this.panning = true;
			this.panStartX = x;
			this.panStartY = y;
			this.panOriginX = this.panX;
			this.panOriginY = this.panY;
			return true;
		}

		if (this.inViewport(x, y) && (button == 0 || button == 1)) {
			if (this.canvasId.isEmpty()) {
				CanvasStore.INSTANCE.setStatus("还没有选择画布，按 L 打开列表", UiKit.WARN);
				return true;
			}

			this.tempEraser = button == 1;
			this.dragging = true;
			this.hasLast = false;
			this.strokeFlushed = false;
			this.strokePoints.clear();
			this.beginStrokeAt(x, y);
			return true;
		}

		return false;
	}

	@Override
	protected boolean onMouseRelease(int x, int y, int button) {
		if (button == 2 && this.panning) {
			this.panning = false;
			return true;
		}

		if (this.dragging) {
			this.dragging = false;
			this.flushStroke();
			this.hasLast = false;
			this.tempEraser = false;
			return true;
		}

		return false;
	}

	@Override
	protected boolean onMouseScroll(int x, int y, double amount) {
		if (this.inViewport(x, y) && amount != 0) {
			this.setZoomIndex(this.zoomIndex() + (amount > 0 ? 1 : -1));
			return true;
		}

		return false;
	}

	@Override
	protected boolean onKeyPressed(int keyCode, int scanCode, boolean ctrl, boolean shift) {
		switch (keyCode) {
			case GLFW.GLFW_KEY_1 -> {
				this.setTool(ToolType.PEN);
				return true;
			}
			case GLFW.GLFW_KEY_2 -> {
				this.setTool(ToolType.ERASER);
				return true;
			}
			case GLFW.GLFW_KEY_3 -> {
				this.setTool(ToolType.PAINTBUCKET);
				return true;
			}
			case GLFW.GLFW_KEY_4 -> {
				this.setTool(ToolType.NONE);
				return true;
			}
			case GLFW.GLFW_KEY_Z -> {
				if (ctrl) {
					if (shift) {
						this.sendRedo();
					} else {
						this.sendUndo();
					}

					return true;
				}
			}
			case GLFW.GLFW_KEY_Y -> {
				if (ctrl) {
					this.sendRedo();
					return true;
				}
			}
			case GLFW.GLFW_KEY_S -> {
				if (ctrl) {
					this.sendSync();
					return true;
				}
			}
			case GLFW.GLFW_KEY_K -> {
				this.toggleProtect();
				return true;
			}
			case GLFW.GLFW_KEY_H -> {
				this.readHeldMap();
				return true;
			}
			case GLFW.GLFW_KEY_G -> {
				this.toggleGrid();
				return true;
			}
			case GLFW.GLFW_KEY_L -> {
				this.open(new CanvasListScreen(this));
				return true;
			}
			case GLFW.GLFW_KEY_O -> {
				this.sendServerGui(GuiType.MENU);
				return true;
			}
			case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
				this.setZoomIndex(this.zoomIndex() + 1);
				return true;
			}
			case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
				this.setZoomIndex(this.zoomIndex() - 1);
				return true;
			}
			case GLFW.GLFW_KEY_R -> {
				this.panX = 0;
				this.panY = 0;
				return true;
			}
			default -> {
			}
		}

		return false;
	}

	// ------------------------------------------------------------------
	// 业务
	// ------------------------------------------------------------------
	private void beginStrokeAt(int screenX, int screenY) {
		int cx = this.toCanvasX(screenX);
		int cy = this.toCanvasY(screenY);

		if (cx < 0 || cy < 0) {
			return;
		}

		this.lastCx = cx;
		this.lastCy = cy;
		this.hasLast = true;
		this.paintPixel(cx, cy);
	}

	/** 每帧根据当前鼠标位置把笔画补全 (不依赖 mouseDragged 回调)。 */
	private void sampleDrag(int mouseX, int mouseY) {
		// 油漆桶是「点一下」的工具，不参与拖拽补线
		if (this.activeTool() == ToolType.PAINTBUCKET) {
			return;
		}

		int cx = this.toCanvasX(mouseX);
		int cy = this.toCanvasY(mouseY);

		if (cx < 0 || cy < 0 || (cx == this.lastCx && cy == this.lastCy)) {
			return;
		}

		if (this.hasLast) {
			this.paintLine(this.lastCx, this.lastCy, cx, cy);
		} else {
			this.paintPixel(cx, cy);
		}

		this.lastCx = cx;
		this.lastCy = cy;
		this.hasLast = true;
	}

	/** Bresenham 补线，保证快速拖动不断线。 */
	private void paintLine(int x0, int y0, int x1, int y1) {
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx = x0 < x1 ? 1 : -1;
		int sy = y0 < y1 ? 1 : -1;
		int err = dx - dy;
		int x = x0;
		int y = y0;
		int guard = 0;

		while (guard++ < 512) {
			if (x != x0 || y != y0) {
				this.paintPixel(x, y);
			}

			if (x == x1 && y == y1) {
				break;
			}

			int e2 = 2 * err;

			if (e2 > -dy) {
				err -= dy;
				x += sx;
			}

			if (e2 < dx) {
				err += dx;
				y += sy;
			}
		}
	}

	/** 按住右键时临时把工具当成橡皮擦用（不改变当前工具选择）。 */
	private ToolType activeTool() {
		return this.tempEraser ? ToolType.ERASER : this.tool;
	}

	/** 本次落笔实际使用的颜色字节。 */
	private byte activeColor() {
		return this.activeTool() == ToolType.ERASER ? MapPalette.TRANSPARENT : this.color;
	}

	private void paintPixel(int x, int y) {
		CanvasData canvas = this.canvas();

		if (canvas == null) {
			return;
		}

		if (!canvas.editable(x, y)) {
			CanvasStore.INSTANCE.setStatus("超出画布范围 (画布为整张 128x128)", UiKit.WARN);
			return;
		}

		if (canvas.isProtected() && !canvas.canEditLocally(CanvasStore.INSTANCE.selfName())) {
			CanvasStore.INSTANCE.setStatus("该画布已锁定保护，无法编辑", UiKit.ERR);
			return;
		}

		// 吸附到逻辑格：服务端一格 = gridN x gridN 个地图像素，发格内任意坐标效果一样，
		// 吸附后本地预览和真正落笔的范围完全一致（不会再出现「画一格盖一大片」）
		int px = canvas.snapX(x);
		int py = canvas.snapY(y);
		int gridN = canvas.gridN();
		byte value = this.activeColor();

		// 油漆桶只允许「单点」发送：0x02 里带 tool=2 会让服务端对包内每个点都泛洪一次，
		// 一次拖拽就能把整张画布填满。这里立刻以单点(0x01)发出，并且不做本地乐观落色。
		if (this.activeTool() == ToolType.PAINTBUCKET) {
			MapDrawClientNetworking.drawPixel(this.canvasId, px, py, ToolType.PAINTBUCKET, value);
			this.strokePoints.clear();
			this.strokeFlushed = true;
			CanvasStore.INSTANCE.markStale(this.canvasId);
			CanvasStore.INSTANCE.setStatus("已发送油漆桶泛洪，等待服务端返回结果", UiKit.TEXT);
			return;
		}

		CanvasStore.INSTANCE.fillLocalCell(this.canvasId, px, py, gridN, value);
		this.strokePoints.add(new int[]{px, py});

		if (this.strokePoints.size() >= MapDrawConfig.get().batchFlushPoints) {
			this.flushStroke();
		}
	}

	/** 把已积累的点发出去：整笔只有单点用 0x01，否则按块发 0x02。 */
	private void flushStroke() {
		if (this.strokePoints.isEmpty()) {
			return;
		}

		ToolType tool = this.activeTool();
		byte value = this.activeColor();

		// 双保险：即使有残留点，油漆桶也只会以单点发出，绝不进 0x02 批量包
		if (tool == ToolType.PAINTBUCKET) {
			int[] first = this.strokePoints.get(0);
			MapDrawClientNetworking.drawPixel(this.canvasId, first[0], first[1], ToolType.PAINTBUCKET, value);
			this.strokeFlushed = true;
			this.strokePoints.clear();
			CanvasStore.INSTANCE.markStale(this.canvasId);
			return;
		}

		if (this.strokePoints.size() == 1 && !this.strokeFlushed) {
			int[] p = this.strokePoints.get(0);
			MapDrawClientNetworking.drawPixel(this.canvasId, p[0], p[1], tool, value);
		} else {
			int chunk = Math.max(1, MapDrawConfig.get().batchFlushPoints);

			for (int i = 0; i < this.strokePoints.size(); i += chunk) {
				int end = Math.min(i + chunk, this.strokePoints.size());
				MapDrawClientNetworking.drawBatch(this.canvasId, tool, value,
						new ArrayList<>(this.strokePoints.subList(i, end)));
			}
		}

		this.strokeFlushed = true;
		this.strokePoints.clear();
	}

	private void setTool(ToolType newTool) {
		this.tool = newTool;
		MapDrawConfig.get().tool = newTool.id();
		MapDrawConfig.save();
		MapDrawClientNetworking.setTool(newTool);
		CanvasStore.INSTANCE.setStatus("切换工具: " + newTool.name(), UiKit.OK);
	}

	private void applyColor(byte value) {
		this.color = value;
		MapDrawConfig.get().color = value & 0xFF;
		MapDrawConfig.save();

		if (MapPalette.isTransparent(value)) {
			MapDrawClientNetworking.setColor(0, 0, 0);
		} else {
			int rgb = MapPalette.rgb(value);
			MapDrawClientNetworking.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
		}

		CanvasStore.INSTANCE.setStatus("画笔颜色: " + MapPalette.name(value) + " (#" + (value & 0xFF) + ")", UiKit.OK);
	}

	private void sendUndo() {
		if (this.canvasId.isEmpty()) {
			return;
		}

		MapDrawClientNetworking.undo(this.canvasId);
		CanvasStore.INSTANCE.markStale(this.canvasId);
		CanvasStore.INSTANCE.setStatus("已发送撤销，稍后自动重新同步", UiKit.TEXT);
	}

	private void sendRedo() {
		if (this.canvasId.isEmpty()) {
			return;
		}

		MapDrawClientNetworking.redo(this.canvasId);
		CanvasStore.INSTANCE.markStale(this.canvasId);
		CanvasStore.INSTANCE.setStatus("已发送重做，稍后自动重新同步", UiKit.TEXT);
	}

	private void sendSync() {
		if (this.canvasId.isEmpty()) {
			CanvasStore.INSTANCE.setStatus("请先选择画布", UiKit.WARN);
			return;
		}

		MapDrawClientNetworking.requestCanvas(this.canvasId);
		CanvasStore.INSTANCE.setStatus("已请求画布数据", UiKit.TEXT);
	}

	/** 从主手地图的 PDC 读出画布 ID 并同步。 */
	private void readHeldMap() {
		if (CanvasStore.INSTANCE.useHeldMap()) {
			this.canvasId = CanvasStore.INSTANCE.currentId();
			this.dragging = false;
			this.strokePoints.clear();
		}
	}

	private void toggleProtect() {
		if (this.canvasId.isEmpty()) {
			return;
		}

		CanvasData canvas = this.canvas();

		if (canvas != null && canvas.isProtected()) {
			MapDrawClientNetworking.deprotect(this.canvasId);
			CanvasStore.INSTANCE.setStatus("已发送解除保护", UiKit.TEXT);
		} else {
			MapDrawClientNetworking.protect(this.canvasId);
			CanvasStore.INSTANCE.setStatus("已发送锁定保护", UiKit.TEXT);
		}

		CanvasStore.INSTANCE.markStale(this.canvasId);
	}

	private void sendServerGui(GuiType type) {
		MapDrawClientNetworking.openServerGui(type, this.canvasId);
		CanvasStore.INSTANCE.setStatus("已请求服务端界面 (" + type.name() + ")", UiKit.TEXT);
	}

	private void toggleGrid() {
		this.showGrid = !this.showGrid;
		MapDrawConfig.get().showGrid = this.showGrid;
		MapDrawConfig.save();
	}

	private void setZoomIndex(int index) {
		int clamped = UiKit.clamp(index, 0, ZOOM_LEVELS.length - 1);
		this.zoom = ZOOM_LEVELS[clamped];
		this.autoFit = false;
		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.zoom = this.zoom;
		cfg.autoFit = false;
		MapDrawConfig.save();
	}

	/** 重新开启「自动适配」。 */
	private void enableAutoFit() {
		this.autoFit = true;
		this.panX = 0;
		this.panY = 0;
		MapDrawConfig.get().autoFit = true;
		MapDrawConfig.save();
		CanvasStore.INSTANCE.setStatus("缩放已改为自动适配画布", UiKit.OK);
	}

	private int zoomIndex() {
		for (int i = 0; i < ZOOM_LEVELS.length; i++) {
			if (ZOOM_LEVELS[i] == this.zoom) {
				return i;
			}
		}

		return 0;
	}

	private CanvasData canvas() {
		return CanvasStore.INSTANCE.get(this.canvasId);
	}

	private byte[] quickColors() {
		byte[] all = new byte[MapPalette.QUICK_16.length + 1];
		all[0] = MapPalette.TRANSPARENT;
		System.arraycopy(MapPalette.QUICK_16, 0, all, 1, MapPalette.QUICK_16.length);
		return all;
	}

	private boolean inViewport(int x, int y) {
		return UiKit.contains(this.viewX, this.viewY, this.viewW, this.viewH, x, y);
	}

	private int toCanvasX(int screenX) {
		int v = Math.floorDiv(screenX - this.originX, Math.max(1, this.zoom));
		return (v >= 0 && v < MapDrawProtocol.CANVAS_W) ? v : -1;
	}

	private int toCanvasY(int screenY) {
		int v = Math.floorDiv(screenY - this.originY, Math.max(1, this.zoom));
		return (v >= 0 && v < MapDrawProtocol.CANVAS_H) ? v : -1;
	}

	private String shortId(String id) {
		if (id == null || id.isEmpty()) {
			return "-";
		}

		return id.length() <= 8 ? id : id.substring(0, 8);
	}

	/** 画板需要 0x0C 自动同步时由外部调用。 */
	public String canvasId() {
		return this.canvasId;
	}

	public void setCanvasId(String id) {
		this.canvasId = id == null ? "" : id;
	}
}
