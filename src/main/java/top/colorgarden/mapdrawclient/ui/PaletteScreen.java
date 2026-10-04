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

	private byte[] quickColors;
	private byte[] canvasColors;
	private int quickX;
	private int quickY;
	private int canvasX;
	private int canvasY;
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

		// 窗口太矮时不显示「画布用色」区块，保证不溢出屏幕
		int quickRows = this.rows(this.quickColors.length);
		int wanted = 16 + quickRows * (CELL + GAP) + 6 + 10 + 2 * (CELL + GAP) + 4
				+ 10 + 3 * 13 + 16 + 10 + 16 + 20 + 16 + 10;
		this.compact = this.height < wanted;
		this.canvasColors = this.compact ? new byte[0] : this.collectCanvasColors();

		int colorRows = this.rows(this.canvasColors.length);
		int contentH = 16 + quickRows * (CELL + GAP) + 6
				+ (colorRows > 0 ? 10 + colorRows * (CELL + GAP) + 4 : 0)
				+ 10 + 3 * 13 + 8
				+ 10 + 16
				+ 20 + 16
				+ 8;

		int panelH = Math.min(contentH, Math.max(120, this.height - 8));
		int panelW = Math.min(this.width - 20, 270);
		int px = (this.width - panelW) / 2;
		int py = (this.height - panelH) / 2;

		this.panelX = px;
		this.panelY = py;
		this.panelW = panelW;
		this.panelH = panelH;

		this.gridW = Math.min(panelW - 16, COLS * (CELL + GAP));
		int gx = px + (panelW - this.gridW) / 2;

		int y = py + 16;
		this.quickX = gx;
		this.quickY = y;
		y += quickRows * (CELL + GAP) + 6;

		this.canvasX = gx;
		this.canvasY = y + 10;

		if (colorRows > 0) {
			y = this.canvasY + colorRows * (CELL + GAP) + 4;
		} else {
			this.canvasY = -1000;
			y -= 6;
		}

		this.sliderX = gx;
		this.sliderW = this.gridW - 30;

		for (int i = 0; i < 3; i++) {
			this.sliderY[i] = y + 10 + i * 13;
		}

		this.previewX = this.sliderX + this.sliderW + 6;
		this.previewY = y + 10;

		int bottom = this.sliderY[2] + 24;

		this.hexField = this.addField(this.sliderX, bottom, 90, 16, "#RRGGBB", "", 16);

		this.addButton(this.sliderX + 94, bottom, 60, 16, "应用 HEX", this::applyHex);
		this.addButton(this.sliderX + 158, bottom, Math.max(30, this.gridW - 158), 16, UiIcon.PALETTE, "服务端",
				() -> this.sendServerPalette()).tooltip = "让服务端打开它自己的调色板";
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

		// 预览 + 映射结果
		int argb = 0xFF000000 | (this.red << 16) | (this.green << 8) | this.blue;
		byte mapped = MapPalette.nearest(argb);
		g.fill(this.previewX, this.previewY, this.previewX + 20, this.previewY + 34, argb);
		g.outline(this.previewX, this.previewY, 20, 34, UiKit.BORDER_HI);
		g.fill(this.previewX, this.previewY + 36, this.previewX + 20, this.previewY + 54,
				0xFF000000 | MapPalette.rgb(mapped));
		g.outline(this.previewX, this.previewY + 36, 20, 18, UiKit.BORDER);
		g.text(this.font, UiKit.formatHex(argb), this.previewX, this.previewY + 58, UiKit.TEXT, false);
		g.text(this.font, "→ " + MapPalette.name(mapped), this.previewX, this.previewY + 68, UiKit.ACCENT, false);
		g.text(this.font, "字节 " + (mapped & 0xFF), this.previewX, this.previewY + 78, UiKit.TEXT_MUTED, false);

		// 当前画笔颜色
		byte current = (byte) MapDrawConfig.get().color;
		g.text(this.font, "当前画笔: " + MapPalette.name(current) + "  (#" + (current & 0xFF) + ")",
				this.quickX, this.quickY - 12, UiKit.TEXT_DIM, false);
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
		// 快捷色 / 画布色
		byte hit = this.hitGrid(x, y, this.quickX, this.quickY, this.quickColors);

		if (hit == -2) {
			return false;
		}

		if (hit != -1) {
			this.applyColor(hit);
			return true;
		}

		hit = this.hitGrid(x, y, this.canvasX, this.canvasY, this.canvasColors);

		if (hit != -1 && hit != -2) {
			this.applyColor(hit);
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
			return true;
		}

		return false;
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

		// 纯本地状态：颜色会随落笔包 (0x01/0x02) 一起发出去，不需要额外发 0x0A
		if (!MapPalette.isTransparent(value)) {
			int rgb = MapPalette.rgb(value);
			this.red = (rgb >> 16) & 0xFF;
			this.green = (rgb >> 8) & 0xFF;
			this.blue = rgb & 0xFF;
		}

		CanvasStore.INSTANCE.setStatus("设置颜色: " + MapPalette.name(value) + " (#" + (value & 0xFF)
				+ ")（本地生效，落笔时随包发送）", UiKit.OK);
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
