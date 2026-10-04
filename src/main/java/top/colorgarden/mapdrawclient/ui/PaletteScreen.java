package top.colorgarden.mapdrawclient.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.GuiType;

/**
 * 调色板：16 色快捷格 + 画布用色统计 + RGB 滑块 + HEX 输入。
 *
 * <p>确定颜色后发送 {@code 0x0A SET_COLOR(int r,int g,int b)}，
 * 同时本地映射到最接近的地图颜色字节用于画板预览。</p>
 */
public class PaletteScreen extends MapDrawScreen {
	private static final int COLS = 9;
	private static final int CELL = 18;
	private static final int GAP = 2;
	/** 右侧预览列宽度。 */
	private static final int PREVIEW_W = 30;
	/** 滑块右侧留给数字的宽度。 */
	private static final int VALUE_W = 26;

	private byte[] quickColors;
	private byte[] canvasColors;
	private byte[] historyColors = new byte[0];
	private int quickX;
	private int quickY;
	private int canvasX;
	private int canvasY;
	private int historyX;
	private int historyY;
	private int gridW;

	private int sliderX;
	private int sliderW;
	private int[] sliderY = new int[3];
	private int sliderH = 10;
	private int activeSlider = -1;

	private int previewX;
	private int previewY;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	private int red = 255;
	private int green = 85;
	private int blue = 85;
	private boolean compact;

	private UiField hexField;

	public PaletteScreen(Screen parent) {
		super(Component.literal("调色板"));
		this.setParent(parent);
	}

	@Override
	protected void init() {
		super.init();
		MapDrawConfig cfg = MapDrawConfig.get();

		this.quickColors = new byte[MapPalette.QUICK_16.length + 1];
		this.quickColors[0] = MapPalette.TRANSPARENT;
		System.arraycopy(MapPalette.QUICK_16, 0, this.quickColors, 1, MapPalette.QUICK_16.length);

		// 历史颜色（最近用过的，最多 HISTORY_MAX 个）
		java.util.List<Integer> hist = cfg.historyColors;

		if (hist == null) {
			hist = new java.util.ArrayList<>();
		}

		int histCount = Math.min(hist.size(), MapDrawConfig.HISTORY_MAX);
		this.historyColors = new byte[histCount];

		for (int i = 0; i < histCount; i++) {
			this.historyColors[i] = (byte) (int) hist.get(i);
		}

		// 窗口太矮时省略「画布用色」区块，保证整体不溢出屏幕
		int quickRowsEstimate = this.rows(this.quickColors.length);
		int wanted = 16 + quickRowsEstimate * (CELL + GAP) + 6
				+ 10 + 2 * (CELL + GAP) + 4
				+ 10 + 2 * (CELL + GAP) + 4
				+ 10 + 3 * 13 + 22 + 16 + 20 + 16 + 8;
		this.compact = this.height < wanted;
		this.canvasColors = this.compact ? new byte[0] : this.collectCanvasColors();

		// 布局：左边是色格 + RGB 滑块，右边单独一列放预览/映射结果，
		// 这样预览绝不会压到滑块数值和下面那排按钮（之前就是挤在一起重叠的）
		int quickRows = this.rows(this.quickColors.length);
		int canvasBlock = this.compact ? 0 : 10 + 2 * (CELL + GAP) + 4;
		int historyRows = this.rows(this.historyColors.length);
		int historyBlock = historyRows > 0 ? 10 + historyRows * (CELL + GAP) + 4 : 0;
		int contentH = 16 + quickRows * (CELL + GAP) + 6 + canvasBlock + historyBlock
				+ 10 + 3 * 13 + 22
				+ 16 + 20 + 16 + 8;

		int panelH = Math.min(contentH, Math.max(140, this.height - 8));
		int panelW = Math.min(this.width - 20, 300);
		int px = (this.width - panelW) / 2;
		int py = (this.height - panelH) / 2;

		this.panelX = px;
		this.panelY = py;
		this.panelW = panelW;
		this.panelH = panelH;

		this.gridW = Math.min(panelW - 16 - PREVIEW_W - 12, COLS * (CELL + GAP));
		int gx = px + 10;

		int y = py + 16;
		this.quickX = gx;
		this.quickY = y;
		y += quickRows * (CELL + GAP) + 6;

		this.canvasX = gx;
		this.canvasY = y + 10;

		if (this.compact) {
			this.canvasY = -1000;
			y -= 6;
		} else {
			y = this.canvasY + 2 * (CELL + GAP) + 4;
		}

		this.historyX = gx;
		this.historyY = y + 10;

		if (historyRows > 0) {
			y = this.historyY + historyRows * (CELL + GAP) + 4;
		} else {
			this.historyY = -1000;
		}

		// 右侧预览列
		this.previewX = gx + this.gridW + 12;
		this.previewY = py + 16;

		this.sliderX = gx;
		this.sliderW = this.gridW - VALUE_W;

		for (int i = 0; i < 3; i++) {
			this.sliderY[i] = y + 10 + i * 13;
		}

		int bottom = this.sliderY[2] + 22;

		this.hexField = this.addField(this.sliderX, bottom, 88, 16, "#RRGGBB", "", 16);

		this.addButton(this.sliderX + 92, bottom, 52, 16, "应用", this::applyHex).tooltip = "应用输入框里的 #RRGGBB";
		this.addButton(this.sliderX + 148, bottom, Math.max(24, this.gridW - 148), 16, UiIcon.PALETTE, "",
				() -> this.sendServerPalette()).tooltip = "让服务端打开它自己的调色板 (0x0B)";
		this.addButton(this.sliderX, bottom + 20, this.gridW, 16, "完成", this::onClose);
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		if (this.activeSlider >= 0) {
			this.updateSlider(mouseX);
		}

		int panelW = this.panelW;
		int panelH = this.panelH;
		int px = this.panelX;
		int py = this.panelY;

		UiKit.panel(g, px, py, panelW, panelH);
		UiKit.header(g, this.font, px + 4, py + 4, panelW - 8, "调色板");

		// 快捷 16 色
		this.renderGrid(g, this.quickX, this.quickY, this.quickColors, mouseX, mouseY);

		// 画布用色 (窗口过矮时省略)
		if (!this.compact) {
			g.text(this.font, "画布用色 (点击即用)", this.canvasX, this.canvasY - 10, UiKit.TEXT_DIM, false);

			if (this.canvasColors.length == 0) {
				g.text(this.font, "暂无画布数据（同步后可见）", this.canvasX, this.canvasY + 4,
						UiKit.TEXT_MUTED, false);
			} else {
				this.renderGrid(g, this.canvasX, this.canvasY, this.canvasColors, mouseX, mouseY);
			}
		}

		// 历史颜色
		if (this.historyColors.length > 0) {
			g.text(this.font, "最近使用（点击即用）", this.historyX, this.historyY - 10, UiKit.TEXT_DIM, false);
			this.renderGrid(g, this.historyX, this.historyY, this.historyColors, mouseX, mouseY);
		}

		// RGB 滑块
		String[] labels = {"R", "G", "B"};
		int[] values = {this.red, this.green, this.blue};
		int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF};

		for (int i = 0; i < 3; i++) {
			g.text(this.font, labels[i], this.sliderX - 8, this.sliderY[i] + 2, colors[i], false);
			UiKit.slider(g, this.sliderX, this.sliderY[i], this.sliderW, this.sliderH, values[i] / 255.0F,
					UiKit.contains(this.sliderX, this.sliderY[i], this.sliderW, this.sliderH, mouseX, mouseY));
			g.text(this.font, String.valueOf(values[i]), this.sliderX + this.sliderW + 4, this.sliderY[i] + 2,
					UiKit.TEXT_DIM, false);
		}

		// 预览 + 映射结果（独立一列，右边不跟任何东西挤）
		int argb = 0xFF000000 | (this.red << 16) | (this.green << 8) | this.blue;
		byte mapped = MapPalette.nearest(argb);
		boolean previewHovered = UiKit.contains(this.previewX, this.previewY, PREVIEW_W, 34, mouseX, mouseY);
		g.fill(this.previewX, this.previewY, this.previewX + PREVIEW_W, this.previewY + 34, argb);
		g.outline(this.previewX, this.previewY, PREVIEW_W, 34,
				previewHovered ? UiKit.ACCENT : UiKit.BORDER_HI);
		g.fill(this.previewX, this.previewY + 36, this.previewX + PREVIEW_W, this.previewY + 52,
				0xFF000000 | MapPalette.rgb(mapped));
		g.outline(this.previewX, this.previewY + 36, PREVIEW_W, 16, UiKit.BORDER);
		g.text(this.font, "→ 地图色", this.previewX, this.previewY + 54, UiKit.TEXT_DIM, false);
		g.text(this.font, MapPalette.name(mapped), this.previewX, this.previewY + 64, UiKit.ACCENT, false);
		g.text(this.font, "字节 " + (mapped & 0xFF), this.previewX, this.previewY + 74, UiKit.TEXT_MUTED, false);

		// 当前画笔颜色（顶栏右侧）
		byte current = (byte) MapDrawConfig.get().color;
		String currentText = "当前画笔: " + MapPalette.name(current) + "  (#" + (current & 0xFF) + ")";
		g.text(this.font, currentText, this.panelX + this.panelW - 8 - this.font.width(currentText), this.panelY + 4,
				UiKit.TEXT_DIM, false);
	}

	private void renderGrid(GuiGraphicsExtractor g, int x0, int y0, byte[] colors, int mouseX, int mouseY) {
		byte current = (byte) MapDrawConfig.get().color;

		for (int i = 0; i < colors.length; i++) {
			int sx = x0 + (i % COLS) * (CELL + GAP);
			int sy = y0 + (i / COLS) * (CELL + GAP);
			boolean hovered = UiKit.contains(sx, sy, CELL, CELL, mouseX, mouseY);
			UiKit.swatch(g, sx, sy, CELL, colors[i], colors[i] == current, hovered);

			if (hovered) {
				UiKit.tooltip(g, this.font, MapPalette.name(colors[i]) + "  #" + (colors[i] & 0xFF),
						mouseX, mouseY, this.width, this.height);
			}
		}
	}

	private int rows(int count) {
		return (count + COLS - 1) / COLS;
	}

	@Override
	protected boolean onMouseClick(int x, int y, int button) {
		// 快捷色
		byte hit = this.hitGrid(x, y, this.quickX, this.quickY, this.quickColors);

		if (hit == -2) {
			return false;
		}

		if (hit != -1) {
			this.applyColor(hit);
			return true;
		}

		// 历史颜色
		hit = this.hitGrid(x, y, this.historyX, this.historyY, this.historyColors);

		if (hit != -1 && hit != -2) {
			this.applyColor(hit);
			return true;
		}

		// 画布用色
		hit = this.hitGrid(x, y, this.canvasX, this.canvasY, this.canvasColors);

		if (hit != -1 && hit != -2) {
			this.applyColor(hit);
			return true;
		}

		// 预览块 = 直接应用当前 RGB 映射出来的地图色
		if (UiKit.contains(this.previewX, this.previewY, PREVIEW_W, 34, x, y)) {
			int argb = 0xFF000000 | (this.red << 16) | (this.green << 8) | this.blue;
			this.applyColor(MapPalette.nearest(argb));
			return true;
		}

		// 滑块
		for (int i = 0; i < 3; i++) {
			if (UiKit.contains(this.sliderX, this.sliderY[i], this.sliderW, this.sliderH, x, y)) {
				this.activeSlider = i;
				this.updateSlider(x);
				return true;
			}
		}

		return false;
	}

	@Override
	protected boolean onMouseRelease(int x, int y, int button) {
		if (this.activeSlider >= 0) {
			this.activeSlider = -1;
			// 松手即应用：拖完滑块就改画笔颜色（之前只改预览，画笔一直是旧颜色）
			this.applyCurrentRgb();
			return true;
		}

		return false;
	}

	/** 把当前 RGB 映射到最近的地图颜色并应用。 */
	private void applyCurrentRgb() {
		int argb = 0xFF000000 | (this.red << 16) | (this.green << 8) | this.blue;
		this.applyColor(MapPalette.nearest(argb));
	}

	/** 返回 -1 = 未命中, -2 = 该网格为空。 */
	private byte hitGrid(int x, int y, int x0, int y0, byte[] colors) {
		if (colors == null || colors.length == 0) {
			return -2;
		}

		for (int i = 0; i < colors.length; i++) {
			int sx = x0 + (i % COLS) * (CELL + GAP);
			int sy = y0 + (i / COLS) * (CELL + GAP);

			if (UiKit.contains(sx, sy, CELL, CELL, x, y)) {
				return colors[i];
			}
		}

		return -1;
	}

	private void updateSlider(int mouseX) {
		float ratio = UiKit.clamp01((mouseX - this.sliderX - 2.0F) / Math.max(1, this.sliderW - 4));
		int value = Math.round(ratio * 255);

		switch (this.activeSlider) {
			case 0 -> this.red = value;
			case 1 -> this.green = value;
			case 2 -> this.blue = value;
			default -> {
			}
		}
	}

	private void applyHex() {
		String text = this.hexField.value.trim();

		if (text.isEmpty()) {
			CanvasStore.INSTANCE.setStatus("请输入 #RRGGBB 或颜色名 (red / &c 等)", UiKit.WARN);
			return;
		}

		int argb = MapPalette.parseColor(text);

		if (argb < 0) {
			CanvasStore.INSTANCE.setStatus("无法解析颜色: " + text, UiKit.ERR);
			return;
		}

		this.red = (argb >> 16) & 0xFF;
		this.green = (argb >> 8) & 0xFF;
		this.blue = argb & 0xFF;
		this.applyColor(MapPalette.nearest(argb));
	}

	@Override
	protected void onFieldSubmit(UiField field) {
		if (field == this.hexField) {
			this.applyHex();
		}
	}

	private void applyColor(byte value) {
		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.color = value & 0xFF;
		MapDrawConfig.save();

		// 纯本地状态：颜色会随落笔包 (0x01/0x02) 一起发出去，不需要额外发 0x0A。
		// 注意：这里不记历史颜色——历史只记「真的画上去过」的颜色，见 BoardScreen.recordUsedColor
		if (!MapPalette.isTransparent(value)) {
			int rgb = MapPalette.rgb(value);
			this.red = (rgb >> 16) & 0xFF;
			this.green = (rgb >> 8) & 0xFF;
			this.blue = rgb & 0xFF;
		}

		CanvasStore.INSTANCE.setStatus("画笔颜色: " + MapPalette.name(value) + " (#" + (value & 0xFF)
				+ ")（已应用到画笔，落笔时随包发送）", UiKit.OK);
	}

	private void sendServerPalette() {
		MapDrawClientNetworking.openServerGui(GuiType.PALETTE, CanvasStore.INSTANCE.currentId());
		CanvasStore.INSTANCE.setStatus("已请求服务端调色板", UiKit.TEXT);
	}

	/** 统计当前画布出现次数最多的 18 个颜色。 */
	private byte[] collectCanvasColors() {
		CanvasData canvas = CanvasStore.INSTANCE.current();

		if (canvas == null) {
			return new byte[0];
		}

		int[] histogram = new int[MapPalette.COLOR_COUNT];
		byte[] pixels = canvas.pixels();

		for (byte b : pixels) {
			histogram[b & 0xFF]++;
		}

		List<Integer> order = new ArrayList<>();

		for (int i = 1; i < MapPalette.COLOR_COUNT; i++) {
			if (histogram[i] > 0) {
				order.add(i);
			}
		}

		order.sort((a, b) -> Integer.compare(histogram[b], histogram[a]));
		int limit = Math.min(18, order.size());
		byte[] result = new byte[limit];

		for (int i = 0; i < limit; i++) {
			result[i] = (byte) (int) order.get(i);
		}

		return result;
	}
}
