package top.colorgarden.mapdrawclient.ui;

import top.colorgarden.mapdrawclient.compat.Compat;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.canvas.EditHistory;
import top.colorgarden.mapdrawclient.canvas.HeldMapProbe;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.input.MapDrawKeys;
import top.colorgarden.mapdrawclient.net.DrawSendQueue;
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
	private static final float[] ZOOM_LEVELS = {0.5F, 0.75F, 1F, 1.25F, 1.5F, 1.75F, 2F, 2.5F, 3F, 4F, 5F, 6F, 8F, 10F, 12F, 16F, 20F, 24F, 32F};
	private static final int SWATCH_COLS = 9;
	private static final int SWATCH_CELL = 11;
	private static final int SWATCH_GAP = 1;
	/** 平移时画布至少留在视口里的像素数。 */
	private static final int KEEP_VISIBLE = 24;
	/** 笔刷大小档位（单位：逻辑格）。 */
	private static final int[] BRUSH_SIZES = {1, 2, 3, 4, 5, 6, 8, 10, 12, 16};

	private final String initialCanvasId;
	private String canvasId = "";

	private ToolType tool = ToolType.PEN;
	private byte color = 114;
	/** 当前缩放（每帧向 zoomTarget 缓动，实现平滑放大缩小）。 */
	private float zoom = 1.0F;
	/** 目标缩放。 */
	private float zoomTarget = 1.0F;
	/** 缩放锚点：屏幕坐标 + 对应的画布像素坐标（缓动期间保持该点不动）。 */
	private float zoomAnchorX;
	private float zoomAnchorY;
	private float zoomAnchorCanvasX;
	private float zoomAnchorCanvasY;
	private boolean zoomAnchorValid;
	/** 缓动起止的缩放与 pan：两者按同一个 t 插值，保证运动单调（不会每帧抖动）。 */
	private float zoomStart = 1.0F;
	/** 缓动起止时间戳（按时间插值，不依赖帧率，避免帧时间波动带来的抖动）。 */
	private long zoomAnimStartMs;
	private float zoomAnimFrom = 1.0F;
	private float zoomAnimTo = 1.0F;
	private int zoomPanStartX;
	private int zoomPanStartY;
	private int zoomPanTargetX;
	private int zoomPanTargetY;
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
	private int brushSliderX;
	private int brushSliderY;
	private int brushSliderW;
	private int brushSliderH;
	private boolean brushDragging;

	// 笔画状态
	//#if MC >= 12108
	//#endif
	/** 每帧耗时统计用（每 60 帧打一条 [Perf] 日志）。 */
	private int perfFrames;
	//#if MC >= 12108
	/** Skija 画布渲染器（纹理只分配一次）。 */
	private final top.colorgarden.mapdrawclient.ui.CanvasImageTexture canvasImageTexture = new top.colorgarden.mapdrawclient.ui.CanvasImageTexture();
	/** GPU 画布用的图像（视口大小，内容变化时才重建）。 */
	private com.mojang.blaze3d.platform.NativeImage canvasImage;
	private int canvasFrames;
	private byte[] canvasImagePixels;
	private int canvasImageW = -1;
	private int canvasImageH = -1;
	private int canvasImageZoomKey = Integer.MIN_VALUE;
	private int canvasImageOffX = Integer.MIN_VALUE;
	private int canvasImageOffY = Integer.MIN_VALUE;
	//#endif
	private boolean dragging;
	/** 画布 GPU 贴图缓存（整张一次 blit，代替逐像素 fill）。 */
	private boolean strokeFlushed;
	private boolean tempEraser;
	private boolean panning;
	private int panStartX;
	private int panStartY;
	private int panOriginX;
	private int panOriginY;
	/** 本笔已经发过的逻辑格（大笔刷拖拽去重用）。 */
	private final java.util.Set<Long> strokeCellSet = new java.util.HashSet<>();
	private boolean queueThrottled;
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
		this.zoom = this.zoomTarget = UiKit.clamp((float) cfg.zoom, ZOOM_LEVELS[0], 32.0F);
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
		bx -= 18;
		this.addButton(bx, 3, 16, 16, UiIcon.BACK, "", () -> this.open(new MainMenuScreen(this)))
				.tooltip = "返回控制台菜单（键位 J 也能开；Esc 直接关掉画板）";

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

		// ---- 右面板：笔刷大小（画笔 / 橡皮共用）----
		y = 52;
		this.brushSliderX = x0;
		this.brushSliderY = y + 12;
		this.brushSliderW = inner;
		this.brushSliderH = 10;

		// ---- 右面板：颜色 ----
		y = 92;
		this.colorBarX = x0;
		this.colorBarY = y;
		this.colorBarW = inner;
		this.colorBarH = 14;

		this.addButton(x0, y + 16, inner, 14, UiIcon.PALETTE, "16 色调色板", () -> this.open(new PaletteScreen(this)))
				.tooltip = "打开调色板：16 色 / RGB 滑块 / HEX 输入 / 历史颜色";

		this.swatchX0 = x0;
		this.swatchY0 = y + 32;

		// ---- 右面板：操作 ----
		y = 164;
		UiButton undoButton = this.addButton(x0, y, halfW, 16, UiIcon.UNDO, "撤销", this::clientUndo);
		undoButton.label = () -> {
			int depth = CanvasStore.INSTANCE.history(this.canvasId).undoDepth();
			return depth > 0 ? "撤销 " + depth : "撤销";
		};
		undoButton.tooltip = "本地撤销（客户端自己记录每一步，Ctrl+Z）；服务端撤销在下面一行";

		UiButton redoButton = this.addButton(x0 + halfW + 2, y, halfW, 16, UiIcon.REDO, "重做", this::clientRedo);
		redoButton.label = () -> {
			int depth = CanvasStore.INSTANCE.history(this.canvasId).redoDepth();
			return depth > 0 ? "重做 " + depth : "重做";
		};
		redoButton.tooltip = "本地重做（Ctrl+Y / Ctrl+Shift+Z）";

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

		// 服务端自带的撤销/重做（0x03 / 0x04）保留，但默认按键走本地历史
		y += 18;
		this.addButton(x0, y, halfW, 16, "服务端撤销", this::sendUndo)
				.tooltip = "调用插件自己的 0x03 撤销（插件撤销栈有缺陷，会清空本地历史）";
		this.addButton(x0 + halfW + 2, y, halfW, 16, "服务端重做", this::sendRedo)
				.tooltip = "调用插件自己的 0x04 重做";

		y += 18;
		this.addButton(x0, y, inner, 16, UiIcon.BACK, "返回控制台菜单", () -> this.open(new MainMenuScreen(this)))
				.tooltip = "回到客户端菜单（新建 / 上传 / 调色板 / 画布列表都在那里）";

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
		long __t0 = System.nanoTime();
		int __phase = 0;
		// 平滑缩放：每帧把 zoom 往 zoomTarget 推一点，并保持锚点不动
		this.updateZoomAnimation();

		// 拖滑块时实时更新笔刷大小
		if (this.brushDragging) {
			this.setBrushFromMouse(mouseX);
		}

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
		Compat.text(g, this.font, title, 6, 6, UiKit.ACCENT, false);
		int titleW = this.font.width(title) + 8;

		if (canvas != null) {
			String info = canvas.displayName() + "  " + canvas.logicalSize() + "x" + canvas.logicalSize()
					+ " 逻辑格 (每格 " + canvas.gridN() + "px)"
					+ (canvas.isProtected() ? "  §c已保护" : "")
					+ (canvas.animated() ? "  §b动图" : "")
					+ "  #" + this.shortId(this.canvasId);
			Compat.text(g, this.font, UiKit.ellipsize(this.font, info, this.panelX - titleW - 140), 6 + titleW, 6,
					UiKit.TEXT_DIM, false);
		} else {
			Compat.text(g, this.font, "未选择画布 — 按 L 打开画布列表或输入画布 ID", 6 + titleW, 6, UiKit.WARN, false);
		}

		if (!MapDrawClientNetworking.canSend()) {
			Compat.text(g, this.font, "通道不可用", this.panelX - 74, 6, UiKit.ERR, false);
		}

		// ---- 画布视口 ----
		// 自动适配：整张画布永远铺满整个画布视口。
		// 注意这里是 128（地图像素边），不是 canvas.size()——size 只是逻辑网格分辨率
		// （16x16 的画布底层仍然是整张 128x128，一个逻辑像素 = 8x8 个地图像素）。
		if (this.autoFit) {
			int fit = Math.min(this.viewW, this.viewH) / MapDrawProtocol.CANVAS_W;
			this.zoom = this.zoomTarget = UiKit.clamp((float) fit, ZOOM_LEVELS[0], 32.0F);
		}

		int cw = Math.round(MapDrawProtocol.CANVAS_W * this.zoom);
		int baseX = this.viewX + (this.viewW - cw) / 2;
		int baseY = this.viewY + (this.viewH - cw) / 2;

		// 平移范围：可以把画布拖到画板外面去（画布边缘越过视口），
		// 但至少留 KEEP_VISIBLE 像素可见，免得整张画布找不回来（按 R 可以归位）。
		int maxPanX = Math.max(0, (cw + this.viewW) / 2 - KEEP_VISIBLE);
		int maxPanY = Math.max(0, (cw + this.viewH) / 2 - KEEP_VISIBLE);
		this.panX = UiKit.clamp(this.panX, -maxPanX, maxPanX);
		this.panY = UiKit.clamp(this.panY, -maxPanY, maxPanY);
		this.originX = baseX + this.panX;
		this.originY = baseY + this.panY;

		g.fill(this.viewX, this.viewY, this.viewX + this.viewW, this.viewY + this.viewH, UiKit.VIEWPORT);
		Compat.outline(g, this.viewX, this.viewY, this.viewW, this.viewH, UiKit.BORDER);

		g.enableScissor(this.viewX + 1, this.viewY + 1, this.viewX + this.viewW - 1, this.viewY + this.viewH - 1);

		long __tCanvas0 = System.nanoTime();

		//#if MC >= 12108
		// 画布：走 MC 自己的 GpuTexture 管线（1 次上传 + 1 次 blit），失败自动回退逐像素
		if (!this.renderCanvasGpu(g, canvas, cw)) {
			this.renderCanvasBackground(g, canvas, cw);

			if (canvas != null) {
				this.renderPixels(g, canvas, cw);
			}
		}
		//#else
		//$$ this.renderCanvasBackground(g, canvas, cw);
		//$$
		//$$ if (canvas != null) {
		//$$ 	this.renderPixels(g, canvas, cw);
		//$$ }
		//#endif
		// 先用「锚定在画布坐标上的棋盘格」铺底，再画像素：
		long __tCanvas1 = System.nanoTime();

		// 网格按「逻辑格」画：一格 = gridN x gridN 个地图像素（size=16 时是 8x8 一格），
		// 整张 128x128 都是可画区，所以网格铺满整张画布。
		long __tGrid0 = System.nanoTime();

		if (this.showGrid && canvas != null) {
			int gridN = canvas.gridN();
			float stepF = Math.max(0.2F, gridN * Math.max(0.05F, this.zoom));

			// 格子太挤就别画了（一堆线糊成噪声）；线粗跟着缩放走，放大后不会细得看不见
			if (stepF >= 4.0F) {
				// 恒定 1px 细线（之前随缩放变粗看着糊）
				int thickness = 1;
				int cells = Math.max(1, MapDrawProtocol.CANVAS_W / Math.max(1, gridN));
				int top = Math.max(this.originY, this.viewY);
				int bottom = Math.min(this.originY + cw, this.viewY + this.viewH);
				int left = Math.max(this.originX, this.viewX);
				int right = Math.min(this.originX + cw, this.viewX + this.viewW);

				for (int k = 0; k <= cells; k++) {
					int x = this.originX + Math.round(k * stepF);
					int y = this.originY + Math.round(k * stepF);

					if (x >= this.viewX && x + thickness <= this.viewX + this.viewW) {
						g.fill(x, top, x + thickness, bottom, 0x40FFFFFF);
					}

					if (y >= this.viewY && y + thickness <= this.viewY + this.viewH) {
						g.fill(left, y, right, y + thickness, 0x40FFFFFF);
					}
				}
			}
		}

		// 可用网格边界由 renderPixels 一起处理（压暗 + 描边），这里不再重复画

		// 悬停预览
		this.hoverCx = this.toCanvasX(mouseX);
		this.hoverCy = this.toCanvasY(mouseY);

		if (canvas != null && this.inViewport(mouseX, mouseY) && this.hoverCx >= 0 && this.hoverCy >= 0) {
			// 预览块按逻辑格吸附：服务端一格就是 gridN x gridN 个地图像素。
			// 笔刷大小 > 1 时，预览也要按笔刷覆盖的格子数放大（以点中的格为中心）
			int brush = this.effectiveBrush();
			int half = (brush - 1) / 2;
			int gridN = Math.max(1, canvas.gridN());
			float zoomF = Math.max(0.05F, this.zoom);
			// 四条边都用 round(origin + 格数 * gridN * zoom)：与像素内容/棋盘格同一套取整边界。
			// 之前是「先取整 cellPx 再乘笔刷格数」，缩放时每帧取整方向不同 → 预览框抖。
			int gx = canvas.snapX(this.hoverCx) / gridN;
			int gy = canvas.snapY(this.hoverCy) / gridN;
			int px = Math.round(this.originX + (gx - half) * gridN * zoomF);
			int py = Math.round(this.originY + (gy - half) * gridN * zoomF);
			int px2 = Math.round(this.originX + (gx - half + brush) * gridN * zoomF);
			int py2 = Math.round(this.originY + (gy - half + brush) * gridN * zoomF);
			int cellPx = Math.round(gridN * zoomF);
			int size = px2 - px;
			ToolType preview = this.activeTool();

			if (preview == ToolType.ERASER) {
				g.fill(px, py, px + size, py + size, 0x80FF5555);
			} else if (preview == ToolType.PAINTBUCKET) {
				// 油漆桶不看笔刷大小，只高亮点中的那一格
				g.fill(px + half * cellPx, py + half * cellPx, px + half * cellPx + cellPx,
						py + half * cellPx + cellPx, 0xC0000000 | MapPalette.rgb(this.activeColor()));
				size = cellPx;
			} else if (preview != ToolType.NONE) {
				g.fill(px, py, px + size, py + size, 0xC0000000 | MapPalette.rgb(this.activeColor()));
			}

			Compat.outline(g, px - 1, py - 1, size + 2, size + 2, UiKit.HOVER_OUTLINE);
		}

		g.disableScissor();

		// ---- 右面板装饰文字 ----
		g.fill(this.panelX, 24, this.panelX + this.panelW, this.height - 22, UiKit.PANEL);
		Compat.outline(g, this.panelX, 24, this.panelW, this.height - 46, UiKit.BORDER);

		int x0 = this.panelX + 4;
		int inner = this.panelW - 8;

		UiKit.header(g, this.font, x0, 24, inner, "工具");
		UiKit.header(g, this.font, x0, 52, inner, "笔刷大小");
		UiKit.header(g, this.font, x0, 78, inner, "颜色");
		// 笔刷大小只对画笔/橡皮有意义：油漆桶是单点泛洪，无工具不落笔
		if (this.brushApplies()) {
			int brush = this.brushSize();
			UiKit.slider(g, this.brushSliderX, this.brushSliderY, this.brushSliderW, this.brushSliderH,
					this.brushRatio(), UiKit.contains(this.brushSliderX, this.brushSliderY, this.brushSliderW,
							this.brushSliderH, this.mouseX, this.mouseY));
			String brushText = brush + " 格";
			Compat.text(g, this.font, brushText, this.brushSliderX + this.brushSliderW - this.font.width(brushText),
					this.brushSliderY - 10, UiKit.TEXT_DIM, false);
		} else {
			g.fill(this.brushSliderX, this.brushSliderY, this.brushSliderX + this.brushSliderW,
					this.brushSliderY + this.brushSliderH, UiKit.BTN_DISABLED);
			Compat.outline(g, this.brushSliderX, this.brushSliderY, this.brushSliderW, this.brushSliderH, UiKit.BORDER);
			String note = this.tool == ToolType.PAINTBUCKET ? "油漆桶不用笔刷" : "未选工具";
			Compat.text(g, this.font, note, this.brushSliderX + this.brushSliderW - this.font.width(note),
					this.brushSliderY - 10, UiKit.TEXT_MUTED, false);
		}

		// 当前颜色条
		UiKit.swatch(g, this.colorBarX + 1, this.colorBarY + 1, this.colorBarH - 2, this.color, false,
				UiKit.contains(this.colorBarX, this.colorBarY, this.colorBarW, this.colorBarH, this.mouseX, this.mouseY));
		Compat.outline(g, this.colorBarX, this.colorBarY, this.colorBarW, this.colorBarH, UiKit.BORDER);
		Compat.text(g, this.font, MapPalette.name(this.color) + " #" + (this.color & 0xFF),
				this.colorBarX + this.colorBarH + 3, this.colorBarY + 3, UiKit.TEXT, false);

		// 快捷色
		byte[] quick = this.quickColors();

		for (int i = 0; i < quick.length; i++) {
			int sx = this.swatchX0 + (i % SWATCH_COLS) * (SWATCH_CELL + SWATCH_GAP);
			int sy = this.swatchY0 + (i / SWATCH_COLS) * (SWATCH_CELL + SWATCH_GAP);
			boolean hovered = UiKit.contains(sx, sy, SWATCH_CELL, SWATCH_CELL, this.mouseX, this.mouseY);
			UiKit.swatch(g, sx, sy, SWATCH_CELL, quick[i], quick[i] == this.color, hovered);
		}

		UiKit.header(g, this.font, x0, 152, inner, "操作");

		// ---- 底栏 ----
		g.fill(0, this.height - 20, this.width, this.height, UiKit.HEADER);

		String bottom;

		if (canvas != null && this.hoverCx >= 0 && this.hoverCy >= 0 && this.inViewport(mouseX, mouseY)) {
			byte pixel = canvas.pixel(canvas.snapX(this.hoverCx), canvas.snapY(this.hoverCy));
			bottom = "逻辑格 " + canvas.logicalX(this.hoverCx) + "," + canvas.logicalY(this.hoverCy)
					+ "  (" + canvas.logicalSize() + "x" + canvas.logicalSize()
					+ ", 每格 " + canvas.gridN() + "px)"
					+ "  |  该处像素 " + MapPalette.name(pixel)
					+ "  |  缩放 x" + (Math.round(this.zoom * 100) / 100.0) + (this.autoFit ? " (自动)" : "");
		} else {
			bottom = "左键画 · 右键按住=临时橡皮 · 中键拖动=平移 · 1/2/3/4 切工具 · ,/. 改笔刷 · Ctrl+Z 撤销 · G 网格 · 滚轮缩放";
		}

		// 有限速积压时优先显示，避免用户以为「画了没反应」
		if (DrawSendQueue.INSTANCE.size() > 0) {
			bottom = "待发送 " + DrawSendQueue.INSTANCE.size() + " 点（限速 "
					+ Math.max(1, MapDrawConfig.get().maxPacketsPerTick) + " 包/"
					+ Math.max(64, MapDrawConfig.get().maxPointsPerTick) + " 点每 tick）  |  " + bottom;
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

		Compat.text(g, this.font, bottom, 6, this.height - 14, UiKit.TEXT_DIM, false);
		Compat.text(g, this.font, status, this.width - 6 - statusW, this.height - 14,
				CanvasStore.INSTANCE.statusFresh(4000) ? CanvasStore.INSTANCE.statusColor() : UiKit.TEXT_MUTED, false);
			long __tEnd = System.nanoTime();
		this.perfFrames++;

		if (this.perfFrames % 60 == 0) {
			top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info(
					"[Perf] 画布 {}us  网格 {}us  整屏 {}us  (zoom={}, cw={}, nanoVg={})",
					(__tCanvas1 - __tCanvas0) / 1000, (__tEnd - __tGrid0) / 1000, (__tEnd - __t0) / 1000,
					Math.round(this.zoom * 100) / 100.0, Math.round(MapDrawProtocol.CANVAS_W * this.zoom),
					false);
		}
}

	/**
	 * 画布底：透明区域的棋盘格。
	 *
	 * <p><b>相位由画布坐标决定</b>（而不是由「这一行透明像素从哪开始」决定），
	 * 所以擦掉/画上像素都不会让图案位移。</p>
	 *
	 * <p><b>方块大小按地图像素算</b>：一格固定等于 {@link #checkerCellMapPx(CanvasData)}
	 * 个地图像素（16x16 画布就是 8 像素 = 正好一个逻辑格），再乘缩放得到屏幕尺寸。
	 * 之前是「格子 = clamp(zoom,2,16) 个屏幕单位」，等于放大缩小时一格代表的地图像素数
	 * 还会变（zoom=1 时一格 2 像素、zoom=32 时一格半像素），完全看不出画布真实分辨率。</p>
	 */
	private void renderCanvasBackground(GuiGraphicsExtractor g, CanvasData canvas, int cw) {
		// 一格 = 一个逻辑格（跟画布分辨率走），缩放到屏幕；最小 2 像素免得 128 画布缩到 1 倍时糊成噪点
		int x0 = Math.max(this.originX, this.viewX + 1);
		int y0 = Math.max(this.originY, this.viewY + 1);
		int x1 = Math.min(this.originX + cw, this.viewX + this.viewW - 1);
		int y1 = Math.min(this.originY + cw, this.viewY + this.viewH - 1);

		if (x1 <= x0 || y1 <= y0) {
			return;
		}

		float step = Math.max(0.2F, this.cellStepPx(canvas));
		int firstCellX = (int) Math.floor((x0 - this.originX) / step);
		int firstCellY = (int) Math.floor((y0 - this.originY) / step);
		int lastCellX = (int) Math.floor((x1 - this.originX) / step);
		int lastCellY = (int) Math.floor((y1 - this.originY) / step);

		if ((long) (lastCellX - firstCellX + 1) * (lastCellY - firstCellY + 1) > 1024) {
			g.fill(x0, y0, x1, y1, UiKit.CHECK_B);
			return;
		}

		for (int cy = firstCellY; cy <= lastCellY; cy++) {
			int top = Math.max(this.originY + Math.round(cy * step), y0);
			int bottom = Math.min(this.originY + Math.round((cy + 1) * step), y1);

			for (int cx = firstCellX; cx <= lastCellX; cx++) {
				int left = Math.max(this.originX + Math.round(cx * step), x0);
				int right = Math.min(this.originX + Math.round((cx + 1) * step), x1);
				boolean light = (((cx + cy) & 1) == 0);
				g.fill(left, top, right, bottom, light ? UiKit.CHECK_A : UiKit.CHECK_B);
			}
		}
	}

	/**
	 * 棋盘格一格的边长（单位：地图像素）。
	 *
	 * <p><b>跟着画布分辨率走</b>：一格 = 一个逻辑格（{@code 128 / size} 个地图像素）。
	 * 16x16 画布 → 8 像素一格；32 → 4；64 → 2；128 → <b>1 个地图像素一格</b>。
	 * 所以画布尺寸一变、或者缩放一变，格子的实际大小都会跟着变，
	 * 但相位永远锚在画布原点上（画像素/擦像素不会让图案位移）。</p>
	 */
	/**
	 * 一个逻辑格在屏幕上的边长（float）。
	 *
	 * <p>棋盘格和网格线<b>必须</b>用同一套边界：两者各自取整就会互相错位、缩放时还会抖。</p>
	 */
	private float cellStepPx(CanvasData canvas) {
		return this.checkerCellMapPx(canvas) * Math.max(0.05F, this.zoom);
	}

	private int checkerCellMapPx(CanvasData canvas) {
		int gridN = canvas == null ? 8 : canvas.gridN();
		// 一格 = 一个逻辑格（与网格线一致），最小 2 个地图像素（避免 128 画布时上万个 fill）
		return Math.max(2, Math.min(gridN, MapDrawProtocol.CANVAS_W));
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
		float zoom = Math.max(0.05F, this.zoom);

		// 每行的色段 (x0, x1, 颜色)；相邻的「完全相同的行」合并成一次 fill
		int[] runX0 = new int[MapDrawProtocol.CANVAS_W + 1];
		int[] runX1 = new int[MapDrawProtocol.CANVAS_W + 1];
		int[] runColor = new int[MapDrawProtocol.CANVAS_W + 1];
		int[] prevX0 = new int[MapDrawProtocol.CANVAS_W + 1];
		int[] prevX1 = new int[MapDrawProtocol.CANVAS_W + 1];
		int[] prevColor = new int[MapDrawProtocol.CANVAS_W + 1];
		int prevCount = 0;
		int blockStartRow = 0;

		for (int y = 0; y < MapDrawProtocol.CANVAS_H; y++) {
			int sy = Math.round(this.originY + y * zoom);
			int count = 0;

			if (sy + zoom >= this.viewY && sy <= viewBottom) {
				int rowBase = y * MapDrawProtocol.CANVAS_W;
				int x = 0;

				while (x < MapDrawProtocol.CANVAS_W) {
					byte value = pixels[rowBase + x];
					int runStart = x;
					x++;

					while (x < MapDrawProtocol.CANVAS_W && pixels[rowBase + x] == value) {
						x++;
					}

					if (value == 0) {
						continue;
					}

					int sx = Math.round(this.originX + runStart * zoom);
					int ex = Math.round(this.originX + x * zoom);

					if (ex < this.viewX || sx > viewRight) {
						continue;
					}

					runX0[count] = sx;
					runX1[count] = ex;
					runColor[count] = MapPalette.argb(value);
					count++;
				}
			}

			boolean sameAsPrev = count == prevCount;

			if (sameAsPrev) {
				for (int i = 0; i < count; i++) {
					if (runX0[i] != prevX0[i] || runX1[i] != prevX1[i] || runColor[i] != prevColor[i]) {
						sameAsPrev = false;
						break;
					}
				}
			}

			if (sameAsPrev) {
				continue;
			}

			if (prevCount > 0) {
				int top = Math.round(this.originY + blockStartRow * zoom);

				for (int i = 0; i < prevCount; i++) {
					g.fill(prevX0[i], top, prevX1[i], sy, prevColor[i]);
				}
			}

			System.arraycopy(runX0, 0, prevX0, 0, count);
			System.arraycopy(runX1, 0, prevX1, 0, count);
			System.arraycopy(runColor, 0, prevColor, 0, count);
			prevCount = count;
			blockStartRow = y;
		}

		if (prevCount > 0) {
			int top = Math.round(this.originY + blockStartRow * zoom);
			int bottom = Math.round(this.originY + MapDrawProtocol.CANVAS_H * zoom);

			for (int i = 0; i < prevCount; i++) {
				g.fill(prevX0[i], top, prevX1[i], bottom, prevColor[i]);
			}
		}
	}

	// ------------------------------------------------------------------
	// 输入
	// ------------------------------------------------------------------
	@Override
	protected boolean onMouseClick(int x, int y, int button) {
		// 笔刷大小滑块（油漆桶 / 无工具时禁用）
		if (UiKit.contains(this.brushSliderX, this.brushSliderY - 2, this.brushSliderW, this.brushSliderH + 6, x, y)) {
			if (!this.brushApplies()) {
				CanvasStore.INSTANCE.setStatus(this.tool == ToolType.PAINTBUCKET
						? "油漆桶是单点泛洪，不用笔刷大小（切到画笔/橡皮再调）"
						: "当前没有工具，笔刷大小不生效", UiKit.WARN);
				return true;
			}

			this.brushDragging = true;
			this.setBrushFromMouse(x);
			return true;
		}

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
			this.strokeCellSet.clear();
			this.queueThrottled = false;
			DrawSendQueue.INSTANCE.begin(this.canvasId);
			// 一次按下 = 一步历史：这一笔（含油漆桶泛洪）整体算一步
			this.beginHistory();
			this.beginStrokeAt(x, y);
			return true;
		}

		return false;
	}

	@Override
	protected boolean onMouseRelease(int x, int y, int button) {
		if (this.brushDragging) {
			this.brushDragging = false;
			CanvasStore.INSTANCE.setStatus("笔刷大小: " + this.brushSize() + " 格（画笔与橡皮共用）", UiKit.OK);
			return true;
		}

		if (button == 2 && this.panning) {
			this.panning = false;
			return true;
		}

		if (this.dragging) {
			this.dragging = false;
			this.flushStroke();

			// 油漆桶要等服务端泛洪结果，先把这一步挂着，同步回来再结算
			if (this.activeTool() != ToolType.PAINTBUCKET) {
				this.commitHistory();
			}

			this.hasLast = false;
			this.tempEraser = false;
			return true;
		}

		return false;
	}

	@Override
	public void onClose() {
		// 中途退出：把还挂着的一步结算掉，免得本地像素改了却没进历史
		this.commitHistory();
		super.onClose();
	}

	@Override
	protected boolean onMouseScroll(int x, int y, double amount) {
		if (this.inViewport(x, y) && amount != 0) {
			this.setZoomIndex(this.zoomIndex() + (amount > 0 ? 1 : -1));
			return true;
		}

		return false;
	}

	/**
	 * 画板快捷键全部走注册过的按键绑定（{@link MapDrawKeys#actionFor}），
	 * 所以能在「选项 → 控制 → 按键绑定 → MapDraw Client」里改键。
	 *
	 * <p>26.2+ 由这里按事件匹配；旧版本没有事件对象，由 {@link MapDrawKeys} 的 tick 循环
	 * 调用 {@link #onKeybindTriggered}。</p>
	 */
	//#if MC >= 12110
	@Override
	protected boolean onKeyEvent(net.minecraft.client.input.KeyEvent event) {
		boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
		return this.performAction(MapDrawKeys.actionFor(event), shift);
	}
	//#endif

	/** 旧版本的按键派发入口。 */
	public boolean onKeybindTriggered(MapDrawKeys.Action action) {
		return this.performAction(action, false);
	}

	/** 执行一个按键动作。 */
	private boolean performAction(MapDrawKeys.Action action, boolean shift) {
		switch (action) {
			case TOOL_PEN -> this.setTool(ToolType.PEN);
			case TOOL_ERASER -> this.setTool(ToolType.ERASER);
			case TOOL_BUCKET -> this.setTool(ToolType.PAINTBUCKET);
			case TOOL_NONE -> this.setTool(ToolType.NONE);
			case BRUSH_DOWN -> this.stepBrushSize(-1);
			case BRUSH_UP -> this.stepBrushSize(1);
			case UNDO -> {
				// 按住 Shift 的撤销 = 重做（跟大多数画图软件一致）
				if (shift) {
					this.clientRedo();
				} else {
					this.clientUndo();
				}
			}
			case REDO -> this.clientRedo();
			case SYNC -> this.sendSync();
			case PROTECT -> this.toggleProtect();
			case GRID -> this.toggleGrid();
			case RESET_PAN -> {
				this.panX = 0;
				this.panY = 0;
			}
			case CANVAS_LIST -> this.open(new CanvasListScreen(this));
			case SERVER_MENU -> this.sendServerGui(GuiType.MENU);
			case READ_HELD -> this.readHeldMap();
			case ZOOM_IN -> this.setZoomIndex(this.zoomIndex() + 1);
			case ZOOM_OUT -> this.setZoomIndex(this.zoomIndex() - 1);
			case BACK_MENU -> this.open(new MainMenuScreen(this));
			default -> {
				return false;
			}
		}

		return true;
	}

	@Override
	protected boolean onKeyPressed(int keyCode, int scanCode, boolean ctrl, boolean shift) {
		// 小键盘的 +/- 原版按键绑定管不到，这里补一下
		switch (keyCode) {
			case GLFW.GLFW_KEY_KP_ADD -> {
				this.setZoomIndex(this.zoomIndex() + 1);
				return true;
			}
			case GLFW.GLFW_KEY_KP_SUBTRACT -> {
				this.setZoomIndex(this.zoomIndex() - 1);
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
	/** 当前笔刷大小（逻辑格）。 */
	private int brushSize() {
		int value = MapDrawConfig.get().brushSize;
		return UiKit.clamp(value, 1, BRUSH_SIZES[BRUSH_SIZES.length - 1]);
	}

	/** 笔刷大小是否对当前工具生效（只有画笔和橡皮吃笔刷大小）。 */
	private boolean brushApplies() {
		ToolType tool = this.activeTool();
		return tool == ToolType.PEN || tool == ToolType.ERASER;
	}

	/** 笔刷大小在滑块上的位置比例。 */
	private float brushRatio() {
		int size = this.brushSize();

		for (int i = 0; i < BRUSH_SIZES.length; i++) {
			if (BRUSH_SIZES[i] >= size) {
				return (float) i / (BRUSH_SIZES.length - 1);
			}
		}

		return 1.0F;
	}

	/** 按鼠标位置设置笔刷大小（吸附到档位）。 */
	private void setBrushFromMouse(int mouseX) {
		float ratio = UiKit.clamp01((mouseX - this.brushSliderX - 2.0F) / Math.max(1, this.brushSliderW - 4));
		int index = Math.round(ratio * (BRUSH_SIZES.length - 1));
		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.brushSize = BRUSH_SIZES[UiKit.clamp(index, 0, BRUSH_SIZES.length - 1)];
		MapDrawConfig.save();
	}

	/** 键盘调整笔刷大小（dir = +1 / -1）。 */
	private void stepBrushSize(int dir) {
		int size = this.brushSize();
		int index = 0;

		for (int i = 0; i < BRUSH_SIZES.length; i++) {
			if (BRUSH_SIZES[i] <= size) {
				index = i;
			}
		}

		int next = UiKit.clamp(index + dir, 0, BRUSH_SIZES.length - 1);
		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.brushSize = BRUSH_SIZES[next];
		MapDrawConfig.save();
		CanvasStore.INSTANCE.setStatus("笔刷大小: " + cfg.brushSize + " 格（画笔与橡皮共用）", UiKit.OK);
	}

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

		while (guard++ < 256) {
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
			this.strokeCellSet.clear();
			DrawSendQueue.INSTANCE.clear();
			this.strokeFlushed = true;
			this.recordUsedColor(value);
			CanvasStore.INSTANCE.markStale(this.canvasId);
			CanvasStore.INSTANCE.setStatus("已发送油漆桶泛洪，等待服务端返回结果", UiKit.TEXT);
			return;
		}

		if (this.activeTool() == ToolType.NONE) {
			CanvasStore.INSTANCE.setStatus("当前没有工具（按 1/2/3 选画笔/橡皮/油漆桶）", UiKit.WARN);
			return;
		}

		// 队列积压太多就先不收新格子：继续拖只会让服务端越追越远
		if (DrawSendQueue.INSTANCE.size() >= Math.max(512, MapDrawConfig.get().maxPendingPoints)) {
			if (!this.queueThrottled) {
				this.queueThrottled = true;
				CanvasStore.INSTANCE.setStatus("绘制太快，已限速：等积压的 " + DrawSendQueue.INSTANCE.size()
						+ " 点发完再继续（放慢拖拽速度或调小笔刷）", UiKit.WARN);
			}

			return;
		}

		// 笔刷：以点中的逻辑格为中心，涂 size x size 个逻辑格（橡皮同样吃这个大小）
		int brush = this.brushApplies() ? this.brushSize() : 1;
		int half = (brush - 1) / 2;
		int baseCellX = canvas.logicalX(px) - half;
		int baseCellY = canvas.logicalY(py) - half;
		ToolType tool = this.activeTool();

		for (int cy = 0; cy < brush; cy++) {
			for (int cx = 0; cx < brush; cx++) {
				int cellX = baseCellX + cx;
				int cellY = baseCellY + cy;
				int cx0 = cellX * gridN;
				int cy0 = cellY * gridN;

				if (cx0 < 0 || cy0 < 0 || cx0 >= MapDrawProtocol.CANVAS_W
						|| cy0 >= MapDrawProtocol.CANVAS_H) {
					continue;
				}

				// 同一笔里同一格只发一次（大笔刷拖拽会反复覆盖同一格）
				long key = ((long) cellX << 20) | (cellY & 0xFFFFFL);

				if (!this.strokeCellSet.add(key)) {
					continue;
				}

				CanvasStore.INSTANCE.fillLocalCell(this.canvasId, cx0, cy0, gridN, value);
				// 只入队，不发包：真正的发送由 DrawSendQueue 每 tick 按配额做，
				// 否则大笔刷 + 快速拖拽会在一帧里发出上千个包，被 Paper 的
				// packet-limiter 当成刷包直接踢（"超出数据包速率限制"）
				DrawSendQueue.INSTANCE.enqueue(this.canvasId, tool, value, cx0, cy0);
			}
		}
	}

	/** 松手：这一笔剩下的点交给 DrawSendQueue 继续发（画板关掉也会发完）。 */
	private void flushStroke() {
		if (this.activeTool() == ToolType.PAINTBUCKET) {
			// 双保险：油漆桶只会以单点发出，绝不进 0x02 批量包
			DrawSendQueue.INSTANCE.clear();
			this.strokeCellSet.clear();
			return;
		}

		DrawSendQueue.INSTANCE.tick();
	}

	private void setTool(ToolType newTool) {
		this.tool = newTool;
		MapDrawConfig.get().tool = newTool.id();
		MapDrawConfig.save();

		// 切工具是纯客户端状态：0x01 / 0x02 落笔包里本来就带工具字节，
		// 没必要每点一次就发一次 0x09（要同步给插件自己的手势时，用控制台菜单里的「同步工具/颜色」）
		CanvasStore.INSTANCE.setStatus("切换工具: " + newTool.name() + "（本地生效，落笔时随包发送）", UiKit.OK);
	}

	private void applyColor(byte value) {
		this.color = value;
		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.color = value & 0xFF;
		MapDrawConfig.save();

		// 同理：颜色也在落笔包里，本地记下来就行，不发 0x0A。
		// 历史颜色只记「真的画上去过」的，见 recordUsedColor
		CanvasStore.INSTANCE.setStatus("画笔颜色: " + MapPalette.name(value) + " (#" + (value & 0xFF)
				+ ")（本地生效，落笔时随包发送）", UiKit.OK);
	}

	/**
	 * 记录「这个颜色真的被用来画了」到历史颜色。
	 *
	 * <p>选中/预览颜色不算，只有落笔（含油漆桶）才记，避免历史里全是拖滑块试出来的颜色。</p>
	 */
	private void recordUsedColor(byte value) {
		if (value == 0 || MapPalette.isTransparent(value)) {
			return;
		}

		if (MapDrawConfig.get().pushHistoryColor(value & 0xFF)) {
			MapDrawConfig.save();
		}
	}

	// ------------------------------------------------------------------
	// 本地撤销 / 重做
	// ------------------------------------------------------------------

	/** 开始记录一步（按下鼠标时调用）。 */
	private void beginHistory() {
		CanvasData canvas = this.canvas();

		if (canvas != null) {
			CanvasStore.INSTANCE.history(this.canvasId).begin(canvas.pixels());
		}
	}

	/** 结算一步（松手 / 退出界面时调用）。 */
	private void commitHistory() {
		CanvasData canvas = this.canvas();

		if (canvas == null) {
			return;
		}

		EditHistory history = CanvasStore.INSTANCE.history(this.canvasId);

		if (history.commit(canvas.pixels())) {
			CanvasStore.INSTANCE.setStatus("已记录一步（本地撤销栈 " + history.undoDepth() + " 步，Ctrl+Z 撤销）",
					UiKit.OK);
		}
	}

	private void clientUndo() {
		CanvasData canvas = this.canvas();

		if (canvas == null) {
			CanvasStore.INSTANCE.setStatus("还没选择画布", UiKit.WARN);
			return;
		}

		EditHistory history = CanvasStore.INSTANCE.history(this.canvasId);
		EditHistory.Step step = history.undo();

		if (step == null) {
			CanvasStore.INSTANCE.setStatus("本地撤销栈是空的（服务端撤销用「服务端撤销」按钮，0x03）", UiKit.WARN);
			return;
		}

		int sent = EditHistory.applyStep(canvas, step, false);
		CanvasStore.INSTANCE.setStatus("已本地撤销 " + step.size() + " 个像素（发回服务端 "
				+ sent + " 个格；可重做 " + history.redoDepth() + " 步）", UiKit.OK);
	}

	private void clientRedo() {
		CanvasData canvas = this.canvas();

		if (canvas == null) {
			CanvasStore.INSTANCE.setStatus("还没选择画布", UiKit.WARN);
			return;
		}

		EditHistory history = CanvasStore.INSTANCE.history(this.canvasId);
		EditHistory.Step step = history.redo();

		if (step == null) {
			CanvasStore.INSTANCE.setStatus("没有可重做的步骤", UiKit.WARN);
			return;
		}

		int sent = EditHistory.applyStep(canvas, step, true);
		CanvasStore.INSTANCE.setStatus("已本地重做 " + step.size() + " 个像素（发回服务端 "
				+ sent + " 个格）", UiKit.OK);
	}

	/** 插件自己的撤销（0x03）：会清空本地历史，避免两边状态打架。 */
	private void sendUndo() {
		if (this.canvasId.isEmpty()) {
			return;
		}

		MapDrawClientNetworking.undo(this.canvasId);
		CanvasStore.INSTANCE.history(this.canvasId).clear();
		CanvasStore.INSTANCE.markStale(this.canvasId);
		CanvasStore.INSTANCE.setStatus("已发送服务端撤销 (0x03)，本地历史已清空并会重新同步", UiKit.TEXT);
	}

	/** 插件自己的重做（0x04）。 */
	private void sendRedo() {
		if (this.canvasId.isEmpty()) {
			return;
		}

		MapDrawClientNetworking.redo(this.canvasId);
		CanvasStore.INSTANCE.history(this.canvasId).clear();
		CanvasStore.INSTANCE.markStale(this.canvasId);
		CanvasStore.INSTANCE.setStatus("已发送服务端重做 (0x04)，本地历史已清空并会重新同步", UiKit.TEXT);
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
			this.strokeCellSet.clear();
			DrawSendQueue.INSTANCE.clear();
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

	/** 滚轮 / +- 缩放：以**鼠标光标**为锚点（光标底下的那个画布像素缩放前后不动）。 */
	private void setZoomIndex(int index) {
		int clamped = UiKit.clamp(index, 0, ZOOM_LEVELS.length - 1);
		boolean inViewport = this.inViewport(this.mouseX, this.mouseY);
		int anchorX = inViewport ? this.mouseX : this.viewX + this.viewW / 2;
		int anchorY = inViewport ? this.mouseY : this.viewY + this.viewH / 2;
		this.autoFit = false;
		this.zoomAt(ZOOM_LEVELS[clamped], anchorX, anchorY);
	}

	/**
	 * 把缩放改到 {@code newZoom}，并保证 (anchorX, anchorY) 处的画布内容保持不动。
	 *
	 * <p>之前只改 zoom，画布永远以自身中心缩放，放大后想看的那个像素早就跑到屏幕外了。</p>
	 */
			private void zoomAt(float newZoom, int anchorX, int anchorY) {
		float nz = UiKit.clamp(newZoom, ZOOM_LEVELS[0], ZOOM_LEVELS[ZOOM_LEVELS.length - 1]);
		float canvasX = (anchorX - this.originX) / Math.max(0.001F, this.zoom);
		float canvasY = (anchorY - this.originY) / Math.max(0.001F, this.zoom);
		this.zoomStart = this.zoom;
		this.zoomAnimFrom = this.zoom;
		this.zoomAnimTo = nz;
		this.zoomAnimStartMs = System.currentTimeMillis();
		this.zoomPanStartX = this.panX;
		this.zoomPanStartY = this.panY;
		int cwEnd = Math.round(MapDrawProtocol.CANVAS_W * nz);
		this.zoomPanTargetX = Math.round(anchorX - canvasX * nz) - (this.viewX + (this.viewW - cwEnd) / 2);
		this.zoomPanTargetY = Math.round(anchorY - canvasY * nz) - (this.viewY + (this.viewH - cwEnd) / 2);
		this.zoomAnchorValid = true;
		this.zoomTarget = nz;
		this.autoFit = false;
	}

	/** 每帧把缩放往目标值推一点，并保持锚点不动。 */
			private void updateZoomAnimation() {
		if (this.zoom == this.zoomTarget) {
			return;
		}

		// 按时间做 ease-out（cubic）：跟帧率解耦，帧时间波动也不会抖
		float p = (System.currentTimeMillis() - this.zoomAnimStartMs) / 160.0F;

		if (p > 1.0F) {
			p = 1.0F;
		}

		float inv = 1.0F - p;
		float eased = 1.0F - inv * inv * inv;
		this.zoom = this.zoomAnimFrom + (this.zoomAnimTo - this.zoomAnimFrom) * eased;
		this.panX = Math.round(this.zoomPanStartX + (this.zoomPanTargetX - this.zoomPanStartX) * eased);
		this.panY = Math.round(this.zoomPanStartY + (this.zoomPanTargetY - this.zoomPanStartY) * eased);

		if (p >= 1.0F) {
			this.zoom = this.zoomTarget;
			this.panX = this.zoomPanTargetX;
			this.panY = this.zoomPanTargetY;
			MapDrawConfig cfg = MapDrawConfig.get();
			cfg.zoom = Math.round(this.zoom);
			cfg.autoFit = false;
			MapDrawConfig.save();
		}
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
		int best = 0;
		float bestDiff = Float.MAX_VALUE;

		for (int i = 0; i < ZOOM_LEVELS.length; i++) {
			float d = Math.abs(ZOOM_LEVELS[i] - this.zoomTarget);

			if (d < bestDiff) {
				bestDiff = d;
				best = i;
			}
		}

		return best;
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
		int v = (int) Math.floor((screenX - this.originX) / Math.max(0.05F, this.zoom));
		return (v >= 0 && v < MapDrawProtocol.CANVAS_W) ? v : -1;
	}

	private int toCanvasY(int screenY) {
		int v = (int) Math.floor((screenY - this.originY) / Math.max(0.05F, this.zoom));
		return (v >= 0 && v < MapDrawProtocol.CANVAS_H) ? v : -1;
	}

	private String shortId(String id) {
		if (id == null || id.isEmpty()) {
			return "-";
		}

		return id.length() <= 8 ? id : id.substring(0, 8);
	}

	/**
	 * 当前有效笔刷格数：开了压感且当前有笔输入时，按压力在
	 * {@code pressureMinBrush ~ pressureMaxBrush} 之间映射；否则用配置里的固定笔刷。
	 */
	private int effectiveBrush() {
		MapDrawConfig cfg = MapDrawConfig.get();
		int base = Math.max(1, cfg.brushSize);

		if (!cfg.tabletEnabled) {
			return base;
		}

		float pressure = top.colorgarden.mapdrawclient.input.TabletInput.INSTANCE.pressure();

		if (pressure < 0.0F) {
			return base;
		}

		int min = Math.max(1, Math.min(cfg.pressureMinBrush, cfg.pressureMaxBrush));
		int max = Math.max(min, Math.max(cfg.pressureMinBrush, cfg.pressureMaxBrush));
		float curved = top.colorgarden.mapdrawclient.input.TabletInput.applyCurve(pressure, cfg.pressureCurve);
		return Math.max(min, Math.min(max, Math.round(min + (max - min) * curved)));
	}

	/** 是否正在绘制（有未发完的点 / 鼠标还按着）：自动重同步要避开这个窗口，免得把本地笔画冲掉。 */
	public boolean isBusy() {
		return this.dragging || top.colorgarden.mapdrawclient.net.DrawSendQueue.INSTANCE.size() > 0;
	}

	/** 画板需要 0x0C 自动同步时由外部调用。 */
	public String canvasId() {
		return this.canvasId;
	}

	public void setCanvasId(String id) {
		this.canvasId = id == null ? "" : id;
	}
	/** 用 MC 的 GpuTexture 管线画布；成功返回 true。 */
	private boolean renderCanvasGpu(GuiGraphicsExtractor g, CanvasData canvas, int cw) {
		if (canvas == null) {
			return false;
		}

		int vx = this.viewX + 1;
		int vy = this.viewY + 1;
		int vw = this.viewW - 2;
		int vh = this.viewH - 2;

		if (vw <= 0 || vh <= 0) {
			return false;
		}

		try {
			byte[] pixels = canvas.pixels();
			int offX = this.originX - vx;
			int offY = this.originY - vy;
			int zoomKey = Math.round(this.zoom * 64.0F);
			boolean same = this.canvasImage != null && this.canvasImageW == vw && this.canvasImageH == vh
					&& this.canvasImageZoomKey == zoomKey && this.canvasImageOffX == offX && this.canvasImageOffY == offY
					&& this.canvasImagePixels != null && java.util.Arrays.equals(this.canvasImagePixels, pixels);

			if (!same) {
				if (this.canvasImage == null || this.canvasImageW != vw || this.canvasImageH != vh) {
					if (this.canvasImage != null) {
						this.canvasImage.close();
					}

					this.canvasImage = new com.mojang.blaze3d.platform.NativeImage(
							com.mojang.blaze3d.platform.NativeImage.Format.RGBA, vw, vh, false);
					this.canvasImageW = vw;
					this.canvasImageH = vh;
				}

				top.colorgarden.mapdrawclient.ui.CanvasImageBuilder.build(this.canvasImage, canvas, vx, vy, vw, vh,
						this.originX, this.originY, this.zoom, (int) Math.max(1.0F, this.cellStepPx(canvas)),
						MapDrawConfig.get().showCheckerboard, UiKit.VIEWPORT, MapDrawConfig.get().showGrid,
						this.gridStepN(canvas), UiKit.BORDER);
				this.canvasImagePixels = pixels.clone();
				this.canvasImageZoomKey = zoomKey;
				this.canvasImageOffX = offX;
				this.canvasImageOffY = offY;
			}

			if (++this.canvasFrames % 60 == 1) {
				top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info(
						"[MapDrawClient][画布GPU] 第 {} 帧: 视口 ({}, {}) {}x{}, 画布 origin ({}, {}) cw={}, zoom={}, cell={}, gridN={}, 图像 {}x{}, 重建={}",
						this.canvasFrames, vx, vy, vw, vh, this.originX, this.originY, cw, this.zoom,
						(int) Math.max(1.0F, this.cellStepPx(canvas)), this.gridStepN(canvas),
						this.canvasImageW, this.canvasImageH, !same);
			}

			return this.canvasImageTexture.draw(g, this.canvasImage, vx, vy, vw, vh);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 网格一格的「地图像素数」。 */
	private int gridStepN(CanvasData canvas) {
		return Math.max(1, MapDrawProtocol.CANVAS_W / Math.max(1, this.checkerCellMapPx(canvas) * 2));
	}
}
