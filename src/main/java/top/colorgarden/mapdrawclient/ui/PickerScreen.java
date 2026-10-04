package top.colorgarden.mapdrawclient.ui;

import java.awt.Color;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.canvas.MapPalette;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;

/**
 * 屏幕取色器：读取<b>电脑屏幕上任意位置的像素</b>（包括游戏窗口以外的区域），
 * 自动换算成最接近的 Minecraft 地图颜色字节，并可直接设为画笔颜色。
 *
 * <p>实现用的是 JDK 自带的 {@link Robot#getPixelColor} + {@link MouseInfo}：
 * 不需要任何原生库，也不需要知道窗口位置与 GUI 缩放——直接按操作系统光标坐标取色。</p>
 *
 * <p>用法：把鼠标移到要取的颜色上（可以移出游戏窗口），按「采样」按钮或 <b>空格</b>，
 * 再点「应用为画笔颜色」。</p>
 */
public class PickerScreen extends MapDrawScreen {
	private Robot robot;
	private boolean robotFailed;

	private int cursorScreenX = -1;
	private int cursorScreenY = -1;
	private int sampledArgb = 0xFFFFFFFF;
	private byte sampledMap = 114;
	private boolean hasSample;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int swatchX;
	private int swatchY;
	private int swatchSize = 44;

	public PickerScreen(Screen parent) {
		super(Component.literal("屏幕取色器"));
		this.setParent(parent);
	}

	@Override
	protected void init() {
		super.init();
		this.ensureRobot();

		this.panelW = Math.min(this.width - 16, 300);
		this.panelH = Math.min(this.height - 16, 168);
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = (this.height - this.panelH) / 2;

		this.swatchX = this.panelX + 12;
		this.swatchY = this.panelY + 48;

		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;
		int half = (inner - 6) / 2;

		this.addButton(x0, this.panelY + this.panelH - 46, half, 18, UiIcon.SYNC, "采样(空格)", this::sample);
		this.addButton(x0 + half + 6, this.panelY + this.panelH - 46, half, 18, UiIcon.CHECK, "应用为画笔", this::apply);
		this.addButton(x0, this.panelY + this.panelH - 24, inner, 18, "返回", this::onClose);
	}

	private void ensureRobot() {
		if (this.robot != null || this.robotFailed) {
			return;
		}

		try {
			this.robot = new Robot();
		} catch (Throwable t) {
			this.robotFailed = true;
		}
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		this.ensureRobot();

		// 实时跟踪光标位置（每帧刷新，方便对准像素）
		try {
			Point point = MouseInfo.getPointerInfo() == null ? null : MouseInfo.getPointerInfo().getLocation();

			if (point != null) {
				this.cursorScreenX = point.x;
				this.cursorScreenY = point.y;
			}
		} catch (Throwable ignored) {
			// 没有指针信息就保持上一次
		}

		UiKit.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
		UiKit.header(g, this.font, this.panelX + 4, this.panelY + 4, this.panelW - 8, "屏幕取色器");

		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;

		if (this.robotFailed) {
			g.text(this.font, "无法初始化屏幕取色（Robot 不可用）", x0, this.panelY + 24, UiKit.ERR, false);
			g.text(this.font, "可改用调色板里的画布配色或 HEX 输入", x0, this.panelY + 38, UiKit.TEXT_DIM, false);
			return;
		}

		g.text(this.font, "光标屏幕坐标: " + this.cursorScreenX + ", " + this.cursorScreenY, x0, this.panelY + 20,
				UiKit.TEXT_DIM, false);

		// 预览色块
		g.fill(this.swatchX, this.swatchY, this.swatchX + this.swatchSize, this.swatchY + this.swatchSize,
				0xFF000000 | (this.sampledArgb & 0xFFFFFF));
		g.outline(this.swatchX, this.swatchY, this.swatchSize, this.swatchSize, UiKit.BORDER_HI);

		int textX = this.swatchX + this.swatchSize + 10;
		g.text(this.font, "屏幕色 " + UiKit.formatHex(this.sampledArgb), textX, this.swatchY + 2, UiKit.TEXT, false);
		g.text(this.font, "→ " + MapPalette.name(this.sampledMap), textX, this.swatchY + 14, UiKit.ACCENT, false);
		g.text(this.font, "字节 " + (this.sampledMap & 0xFF), textX, this.swatchY + 26, UiKit.TEXT_MUTED, false);

		int swatch2X = textX;

		if (this.hasSample) {
			g.fill(swatch2X, this.swatchY + 38, swatch2X + 16, this.swatchY + 54,
					0xFF000000 | MapPalette.rgb(this.sampledMap));
			g.outline(swatch2X, this.swatchY + 38, 16, 16, UiKit.BORDER);
		}

		g.text(this.font, "把鼠标移到目标颜色上（可移出游戏窗口）", x0, this.panelY + this.panelH - 62,
				UiKit.TEXT_MUTED, false);
		g.text(this.font, "按空格或「采样」，再点「应用为画笔」", x0, this.panelY + this.panelH - 52,
				UiKit.TEXT_MUTED, false);
	}

	@Override
	protected boolean onKeyPressed(int keyCode, int scanCode, boolean ctrl, boolean shift) {
		if (keyCode == GLFW.GLFW_KEY_SPACE) {
			this.sample();
			return true;
		}

		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			this.apply();
			return true;
		}

		return false;
	}

	private void sample() {
		this.ensureRobot();

		if (this.robot == null) {
			CanvasStore.INSTANCE.setStatus("屏幕取色器不可用", UiKit.ERR);
			return;
		}

		try {
			Point point = MouseInfo.getPointerInfo() == null ? null : MouseInfo.getPointerInfo().getLocation();

			if (point == null) {
				CanvasStore.INSTANCE.setStatus("拿不到光标位置", UiKit.WARN);
				return;
			}

			Color color = this.robot.getPixelColor(point.x, point.y);
			this.cursorScreenX = point.x;
			this.cursorScreenY = point.y;
			this.sampledArgb = 0xFF000000 | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
			this.sampledMap = MapPalette.nearest(this.sampledArgb);
			this.hasSample = true;
			CanvasStore.INSTANCE.setStatus("已取色 " + UiKit.formatHex(this.sampledArgb)
					+ " → " + MapPalette.name(this.sampledMap), UiKit.OK);
		} catch (Throwable t) {
			CanvasStore.INSTANCE.setStatus("取色失败: " + t.getClass().getSimpleName(), UiKit.ERR);
		}
	}

	private void apply() {
		if (!this.hasSample) {
			this.sample();
		}

		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.color = this.sampledMap & 0xFF;
		MapDrawConfig.save();

		if (MapPalette.isTransparent(this.sampledMap)) {
			MapDrawClientNetworking.setColor(0, 0, 0);
		} else {
			int rgb = MapPalette.rgb(this.sampledMap);
			MapDrawClientNetworking.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
		}

		CanvasStore.INSTANCE.setStatus("画笔颜色已设为 " + MapPalette.name(this.sampledMap)
				+ " (#" + (this.sampledMap & 0xFF) + ")", UiKit.OK);
	}
}
