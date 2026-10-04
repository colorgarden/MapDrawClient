package top.colorgarden.mapdrawclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.GuiType;

/**
 * 控制台主菜单 —— 模组唯一的入口界面 (键位 J)，其余界面都从这里进。
 *
 * <p>在游戏里也可以「手持画布地图 → 右键」或「潜行右键画布展示框」直接开画板。</p>
 *
 * <p>布局按窗口高度自适应：行数由 {@code (panelH - 88) / 20} 算出来，
 * 窗口太小会自动少放几行，不会溢出面板。</p>
 */
public class MainMenuScreen extends MapDrawScreen {
	private static final int ROW_H = 16;
	private static final int ROW_SPACING = 20;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public MainMenuScreen(Screen parent) {
		super(Component.literal("MapDraw 控制台"));
		this.setParent(parent);
	}

	@Override
	protected void init() {
		super.init();

		this.panelW = Math.min(this.width - 16, 400);
		this.panelH = Math.min(this.height - 12, 236);
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = (this.height - this.panelH) / 2;

		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;
		int gap = 6;
		int colW = (inner - gap) / 2;
		int x1 = x0 + colW + gap;
		int y = this.panelY + 44;
		int rows = Math.max(1, (this.panelH - 88) / ROW_SPACING);

		for (int i = 0; i < rows; i++) {
			int ry = y + i * ROW_SPACING;

			switch (i) {
				case 0 -> this.addButton(x0, ry, colW, ROW_H, UiIcon.PEN, "打开画板", this::openBoard)
						.tooltip = "也可以直接：手持画布地图 → 右键";
				case 1 -> this.addButton(x0, ry, colW, ROW_H, UiIcon.PALETTE, "调色板",
						() -> this.open(new PaletteScreen(this)));
				case 2 -> this.addButton(x0, ry, colW, ROW_H, UiIcon.LIST, "画布列表 / 输入 ID",
						() -> this.open(new CanvasListScreen(this)));
				case 3 -> this.addButton(x0, ry, colW, ROW_H, UiIcon.NEW, "新建画布",
						() -> this.open(new CreateCanvasScreen(this)));
				case 4 -> {
					UiButton meta = this.addButton(x0, ry, colW, ROW_H, UiIcon.GRID, "画布元数据",
							() -> this.open(new MetaScreen(this, CanvasStore.INSTANCE.currentId())));
					meta.enabled = () -> !CanvasStore.INSTANCE.currentId().isEmpty();
					meta.tooltip = "标题 / 描述 / 尺寸 / 防拷贝（先选一张画布）";
				}
				case 5 -> this.addButton(x0, ry, colW, ROW_H, UiIcon.NEW, "上传图片",
						() -> this.open(new UploadScreen(this)))
						.tooltip = "URL 交给服务端 / 本地文件客户端直接画";
				case 6 -> this.addButton(x0, ry, colW, ROW_H, UiIcon.PALETTE, "屏幕取色器",
						() -> this.open(new PickerScreen(this)))
						.tooltip = "取电脑屏幕任意像素，自动映射到最近的地图颜色";
				default -> {
				}
			}
		}

		for (int i = 0; i < rows; i++) {
			int ry = y + i * ROW_SPACING;

			switch (i) {
				case 0 -> this.addButton(x1, ry, colW, ROW_H, UiIcon.CHECK, "识别手持地图", this::useHeldMap)
						.tooltip = "从主手 / 副手 / 背包的地图里读出画布 ID";
				case 1 -> this.addButton(x1, ry, colW, ROW_H, UiIcon.SYNC, "重新同步", this::requestCanvas)
						.tooltip = "重新拉取当前画布的元数据与 16384 像素";
				case 2 -> {
					UiButton protect = this.addButton(x1, ry, colW, ROW_H, UiIcon.LOCK, "保护 / 解锁", this::toggleProtect);
					protect.enabled = () -> !CanvasStore.INSTANCE.currentId().isEmpty();
					protect.tooltip = "锁定后无法再编辑（作者与管理员可解）";
					protect.selected = () -> {
						CanvasData canvas = CanvasStore.INSTANCE.current();
						return canvas != null && canvas.isProtected();
					};
				}
				case 3 -> this.addServerGuiButton(x1, ry, colW, ROW_H, UiIcon.MENU, "服务端菜单", GuiType.MENU);
				case 4 -> this.addServerGuiButton(x1, ry, colW, ROW_H, UiIcon.PALETTE, "服务端调色板", GuiType.PALETTE);
				default -> {
				}
			}
		}

		// ---- 底部开关 ----
		int bottom = this.panelY + this.panelH - 24;
		int toggleW = (inner - gap * 3) / 4;

		UiButton grid = this.addButton(x0, bottom, toggleW, ROW_H, UiIcon.GRID, "网格", () -> {
			MapDrawConfig cfg = MapDrawConfig.get();
			cfg.showGrid = !cfg.showGrid;
			MapDrawConfig.save();
		});
		grid.selected = () -> MapDrawConfig.get().showGrid;

		UiButton checker = this.addButton(x0 + toggleW + gap, bottom, toggleW, ROW_H, UiIcon.NONE, "棋盘格", () -> {
			MapDrawConfig cfg = MapDrawConfig.get();
			cfg.showCheckerboard = !cfg.showCheckerboard;
			MapDrawConfig.save();
		});
		checker.selected = () -> MapDrawConfig.get().showCheckerboard;

		UiButton theme = this.addButton(x0 + (toggleW + gap) * 2, bottom, toggleW, ROW_H, UiIcon.PALETTE,
				MapDrawConfig.get().lightTheme ? "浅色" : "深色", this::toggleTheme);
		theme.selected = () -> MapDrawConfig.get().lightTheme;
		theme.tooltip = "切换 深色 / 浅色 主题（立即生效并保存）";

		this.addButton(x0 + (toggleW + gap) * 3, bottom, toggleW, ROW_H, UiIcon.CLOSE, "关闭", this::onClose);
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiKit.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
		UiKit.header(g, this.font, this.panelX + 4, this.panelY + 4, this.panelW - 8, "MapDraw 控制台");

		CanvasData canvas = CanvasStore.INSTANCE.current();
		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;

		if (canvas == null) {
			g.text(this.font, UiKit.ellipsize(this.font,
					"当前没有画布：点「新建画布」或「识别手持地图」", inner), x0, this.panelY + 18, UiKit.WARN, false);
		} else {
			String info = canvas.displayName() + "  " + canvas.size() + "x" + canvas.size()
					+ (canvas.isProtected() ? "  已保护" : "")
					+ (canvas.animated() ? "  动图" : "")
					+ "  #" + this.shortId(canvas.id());
			g.text(this.font, UiKit.ellipsize(this.font, info, inner), x0, this.panelY + 18, UiKit.TEXT_DIM, false);
		}

		int colHeaderW = (this.panelW - 26) / 2;
		UiKit.header(g, this.font, x0, this.panelY + 30, colHeaderW, "功能");
		UiKit.header(g, this.font, x0 + colHeaderW + 6, this.panelY + 30, colHeaderW, "服务端 / 同步");

		boolean channel = MapDrawClientNetworking.canSend();
		String status = CanvasStore.INSTANCE.status();
		int maxStatus = Math.max(60, inner - 120);
		status = UiKit.ellipsize(this.font, status, maxStatus);
		int statusW = this.font.width(status);

		g.text(this.font, channel ? "mapdraw:main 就绪" : "通道不可用", x0, this.panelY + this.panelH - 38,
				channel ? UiKit.OK : UiKit.ERR, false);
		g.text(this.font, status, x0 + inner - statusW, this.panelY + this.panelH - 38,
				CanvasStore.INSTANCE.statusFresh(4000) ? CanvasStore.INSTANCE.statusColor() : UiKit.TEXT_MUTED, false);
	}

	// ------------------------------------------------------------------
	/**
	 * 服务端界面按钮：只有「真的有画布」时才可点。
	 * 否则空手、没选画布也能把插件菜单呼出来，会莫名其妙。
	 */
	private void addServerGuiButton(int x, int y, int w, int h, UiIcon icon, String label, GuiType type) {
		UiButton button = this.addButton(x, y, w, h, icon, label, () -> this.sendServerGui(type));
		button.enabled = () -> CanvasStore.INSTANCE.current() != null;
		button.tooltip = "先选中一张画布（识别手持地图 / 画布列表）";
	}

	/** 深色 / 浅色主题切换。 */
	private void toggleTheme() {
		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.lightTheme = !cfg.lightTheme;
		MapDrawConfig.save();
		UiKit.applyTheme(cfg.lightTheme ? UiKit.Theme.LIGHT : UiKit.Theme.DARK);
		CanvasStore.INSTANCE.setStatus("已切换为" + (cfg.lightTheme ? "浅色" : "深色") + "主题", UiKit.OK);
	}

	private void openBoard() {
		top.colorgarden.mapdrawclient.input.BoardOpenHandler.openBoardFromHeldMap();
	}

	private void useHeldMap() {
		CanvasStore.INSTANCE.useHeldMap();
	}

	private void requestCanvas() {
		String id = CanvasStore.INSTANCE.currentId();

		if (id.isEmpty()) {
			CanvasStore.INSTANCE.setStatus("还没有选中画布", UiKit.WARN);
			return;
		}

		MapDrawClientNetworking.requestCanvas(id);
		CanvasStore.INSTANCE.setStatus("已请求画布数据", UiKit.TEXT);
	}

	private void toggleProtect() {
		String id = CanvasStore.INSTANCE.currentId();

		if (id.isEmpty()) {
			return;
		}

		CanvasData canvas = CanvasStore.INSTANCE.current();

		if (canvas != null && canvas.isProtected()) {
			MapDrawClientNetworking.deprotect(id);
			CanvasStore.INSTANCE.setStatus("已发送解除保护", UiKit.TEXT);
		} else {
			MapDrawClientNetworking.protect(id);
			CanvasStore.INSTANCE.setStatus("已发送锁定保护", UiKit.TEXT);
		}

		CanvasStore.INSTANCE.markStale(id);
	}

	private void sendServerGui(GuiType type) {
		MapDrawClientNetworking.openServerGui(type, CanvasStore.INSTANCE.currentId());
		CanvasStore.INSTANCE.setStatus("已请求服务端界面 (" + type.name() + ")", UiKit.TEXT);
	}

	private String shortId(String id) {
		if (id == null || id.isEmpty()) {
			return "-";
		}

		return id.length() <= 8 ? id : id.substring(0, 8);
	}
}
