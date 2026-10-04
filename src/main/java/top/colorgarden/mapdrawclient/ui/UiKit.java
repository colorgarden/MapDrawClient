package top.colorgarden.mapdrawclient.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 画板 UI 的绘制工具集 + 配色主题。
 *
 * <p>整个模组的界面都使用 {@link GuiGraphicsExtractor} 的原始绘制 (fill / outline / text)，
 * 不依赖任何原版控件贴图，因此 26.2 的控件渲染重构不会影响这里的显示效果。</p>
 *
 * <p>配色字段是 <b>非 final</b> 的静态字段，{@link #applyTheme(Theme)} 会整组替换，
 * 界面每帧都重新取色，所以切换主题立即生效、不需要重启或重建界面。</p>
 */
public final class UiKit {
	private UiKit() {
	}

	/** 配色主题。 */
	public enum Theme {
		DARK,
		LIGHT
	}

	// ---- 主题色 (ARGB)，由 applyTheme 赋值 ----
	public static int SCRIM;
	public static int PANEL;
	public static int PANEL_ALT;
	public static int HEADER;
	public static int BORDER;
	public static int BORDER_HI;
	public static int TEXT;
	public static int TEXT_HOVER;
	public static int TEXT_DIM;
	public static int TEXT_MUTED;
	public static int ACCENT;
	public static int OK;
	public static int WARN;
	public static int ERR;
	public static int BTN;
	public static int BTN_HOVER;
	public static int BTN_DISABLED;
	public static int SLOT;
	public static int CHECK_A;
	public static int CHECK_B;
	public static int TOGGLE;
	public static int SLIDER_FILL;
	public static int KNOB;
	public static int VIEWPORT;
	public static int GRID_MAJOR;
	public static int GRID_MINOR;
	public static int DIM;
	public static int HOVER_OUTLINE;

	private static Theme current = Theme.DARK;

	static {
		applyTheme(Theme.DARK);
	}

	public static Theme theme() {
		return current;
	}

	public static void applyTheme(Theme theme) {
		current = theme;

		if (theme == Theme.LIGHT) {
			SCRIM = 0xB8FFFFFF;
			PANEL = 0xF4F5F5F8;
			PANEL_ALT = 0xFFEDEDF1;
			HEADER = 0xFFDCDCE4;
			BORDER = 0xFF9A9AA6;
			BORDER_HI = 0xFF4A4A58;
			TEXT = 0xFF202028;
			TEXT_HOVER = 0xFF000000;
			TEXT_DIM = 0xFF4A4A56;
			TEXT_MUTED = 0xFF7A7A86;
			ACCENT = 0xFF0E6E90;
			OK = 0xFF1B7F3A;
			WARN = 0xFFA85F00;
			ERR = 0xFFC22B2B;
			BTN = 0xFFE4E4EA;
			BTN_HOVER = 0xFFD0D0DC;
			BTN_DISABLED = 0xFFF0F0F4;
			SLOT = 0xFFFFFFFF;
			CHECK_A = 0xFFDCDCE4;
			CHECK_B = 0xFFC2C2CC;
			TOGGLE = 0xFF9FD3E4;
			SLIDER_FILL = 0xFF7FB8C8;
			KNOB = 0xFF3A3A46;
			VIEWPORT = 0xFFFAFAFC;
			GRID_MAJOR = 0x40000000;
			GRID_MINOR = 0x18000000;
			DIM = 0x66000000;
			HOVER_OUTLINE = 0xFF000000;
		} else {
			SCRIM = 0xB8000000;
			PANEL = 0xF0101014;
			PANEL_ALT = 0xFF17171D;
			HEADER = 0xFF23232C;
			BORDER = 0xFF3A3A46;
			BORDER_HI = 0xFF8A8AA0;
			TEXT = 0xFFE6E6EA;
			TEXT_HOVER = 0xFFFFFFFF;
			TEXT_DIM = 0xFF9A9AA4;
			TEXT_MUTED = 0xFF6E6E78;
			ACCENT = 0xFF55FFFF;
			OK = 0xFF55FF55;
			WARN = 0xFFFFD24A;
			ERR = 0xFFFF5555;
			BTN = 0xFF2A2A34;
			BTN_HOVER = 0xFF3C3C4A;
			BTN_DISABLED = 0xFF1C1C22;
			SLOT = 0xFF0C0C10;
			CHECK_A = 0xFF3C3C44;
			CHECK_B = 0xFF2A2A30;
			TOGGLE = 0xFF2E4A56;
			SLIDER_FILL = 0xFF3A6E7A;
			KNOB = 0xFFFFFFFF;
			VIEWPORT = 0xFF08080A;
			GRID_MAJOR = 0x40FFFFFF;
			GRID_MINOR = 0x18FFFFFF;
			DIM = 0x99000000;
			HOVER_OUTLINE = 0xFFFFFFFF;
		}
	}

	/** 面板底 + 边框。 */
	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL);
		g.outline(x, y, w, h, BORDER);
	}

	/** 子面板 (比主面板稍亮/稍暗)。 */
	public static void subPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL_ALT);
		g.outline(x, y, w, h, BORDER);
	}

	/** 分区标题条。 */
	public static void header(GuiGraphicsExtractor g, Font font, int x, int y, int w, String text) {
		g.fill(x, y, x + w, y + 11, HEADER);
		g.text(font, text, x + 3, y + 2, ACCENT, false);
	}

	/** 普通按钮。 */
	public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
			String label, boolean hovered, boolean active) {
		int bg = !active ? BTN_DISABLED : (hovered ? BTN_HOVER : BTN);
		g.fill(x, y, x + w, y + h, bg);
		g.outline(x, y, w, h, !active ? BORDER : (hovered ? BORDER_HI : BORDER));
		int color = !active ? TEXT_MUTED : (hovered ? TEXT_HOVER : TEXT);
		textCentered(g, font, label, x, y, w, h, color);
	}

	/** 带图标的按钮。 */
	public static void iconButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
			UiIcon icon, String label, boolean hovered, boolean active) {
		int bg = !active ? BTN_DISABLED : (hovered ? BTN_HOVER : BTN);
		g.fill(x, y, x + w, y + h, bg);
		g.outline(x, y, w, h, !active ? BORDER : (hovered ? BORDER_HI : BORDER));

		int color = !active ? TEXT_MUTED : (hovered ? TEXT_HOVER : TEXT);
		boolean hasLabel = label != null && !label.isEmpty();

		if (icon != null && icon != UiIcon.NONE) {
			int iconSize = Math.min(16, h - 4);

			if (hasLabel) {
				icon.draw(g, x + 3, y + (h - iconSize) / 2, iconSize, color);
				g.text(font, label, x + iconSize + 5, y + (h - 8) / 2, color, false);
			} else {
				icon.draw(g, x + (w - iconSize) / 2, y + (h - iconSize) / 2, iconSize, color);
			}
		} else {
			textCentered(g, font, label == null ? "" : label, x, y, w, h, color);
		}
	}

	/** 可选中/切换按钮 (选中时描边高亮)。 */
	public static void toggleButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
			UiIcon icon, String label, boolean selected, boolean hovered) {
		int bg = selected ? TOGGLE : (hovered ? BTN_HOVER : BTN);
		g.fill(x, y, x + w, y + h, bg);
		g.outline(x, y, w, h, selected ? ACCENT : (hovered ? BORDER_HI : BORDER));

		int color = selected ? TEXT_HOVER : TEXT;
		int iconSize = Math.min(14, h - 4);

		if (icon != null && icon != UiIcon.NONE) {
			icon.draw(g, x + 2, y + (h - iconSize) / 2, iconSize, color);
			g.text(font, label, x + iconSize + 4, y + (h - 8) / 2, color, false);
		} else {
			textCentered(g, font, label, x, y, w, h, color);
		}
	}

	/** 居中文字。 */
	public static void textCentered(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int h, int color) {
		int tw = font.width(text);
		g.text(font, text, x + (w - tw) / 2, y + (h - 8) / 2, color, false);
	}

	/** 左对齐文字 (自动按宽度裁剪)。 */
	public static void textEllipsized(GuiGraphicsExtractor g, Font font, String text, int x, int y, int maxWidth, int color) {
		g.text(font, ellipsize(font, text, maxWidth), x, y, color, false);
	}

	public static String ellipsize(Font font, String text, int maxWidth) {
		if (text == null) {
			return "";
		}

		if (font.width(text) <= maxWidth) {
			return text;
		}

		String ellipsis = "…";
		int ellipsisWidth = font.width(ellipsis);
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < text.length(); i++) {
			String next = sb.toString() + text.charAt(i);

			if (font.width(next) + ellipsisWidth > maxWidth) {
				break;
			}

			sb.append(text.charAt(i));
		}

		return sb + ellipsis;
	}

	/** 输入框 (cursorIndex >= 0 时绘制光标)。 */
	public static void field(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
			String value, String label, boolean focused, int cursorIndex) {
		if (label != null && !label.isEmpty()) {
			g.text(font, label, x, y - 10, TEXT_DIM, false);
		}

		g.fill(x, y, x + w, y + h, SLOT);
		g.outline(x, y, w, h, focused ? ACCENT : BORDER);

		String shown = value == null ? "" : value;
		shown = ellipsize(font, shown, w - 8);
		g.text(font, shown, x + 3, y + (h - 8) / 2, TEXT, false);

		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caret = clamp(cursorIndex, 0, shown.length());
			int caretX = x + 3 + font.width(shown.substring(0, caret));
			caretX = Math.min(caretX, x + w - 2);
			g.fill(caretX, y + 2, caretX + 1, y + h - 2, ACCENT);
		}
	}

	/** 滑条。 */
	public static void slider(GuiGraphicsExtractor g, int x, int y, int w, int h, float ratio, boolean hovered) {
		g.fill(x, y, x + w, y + h, SLOT);
		g.outline(x, y, w, h, hovered ? BORDER_HI : BORDER);
		int innerW = Math.max(0, w - 4);
		int fillW = (int) (innerW * clamp01(ratio));
		g.fill(x + 2, y + 2, x + 2 + fillW, y + h - 2, SLIDER_FILL);
		int knobX = x + 2 + fillW;

		if (knobX > x + w - 6) {
			knobX = x + w - 6;
		}

		g.fill(knobX, y + 1, knobX + 4, y + h - 1, KNOB);
	}

	/** 颜色格子。 */
	public static void swatch(GuiGraphicsExtractor g, int x, int y, int size, byte colorId, boolean selected, boolean hovered) {
		if (colorId == 0) {
			checkerboard(g, x, y, size, size, Math.max(2, size / 4));
		} else {
			g.fill(x, y, x + size, y + size, 0xFF000000 | (top.colorgarden.mapdrawclient.canvas.MapPalette.rgb(colorId)));
		}

		if (selected) {
			g.outline(x - 1, y - 1, size + 2, size + 2, ACCENT);
			g.outline(x, y, size, size, TEXT_HOVER);
		} else if (hovered) {
			g.outline(x, y, size, size, BORDER_HI);
		} else {
			g.outline(x, y, size, size, BORDER);
		}
	}

	/** 透明棋盘格底纹。 */
	public static void checkerboard(GuiGraphicsExtractor g, int x, int y, int w, int h, int cell) {
		if (w <= 0 || h <= 0) {
			return;
		}

		g.fill(x, y, x + w, y + h, CHECK_B);
		int step = Math.max(2, cell);

		for (int gy = 0; gy < h; gy += step) {
			for (int gx = 0; gx < w; gx += step) {
				if (((gx / step) + (gy / step)) % 2 == 0) {
					g.fill(x + gx, y + gy, Math.min(x + gx + step, x + w), Math.min(y + gy + step, y + h), CHECK_A);
				}
			}
		}
	}

	/** 简易气泡提示 (绘制在鼠标右上方)。 */
	public static void tooltip(GuiGraphicsExtractor g, Font font, String text, int mouseX, int mouseY) {
		if (text == null || text.isEmpty()) {
			return;
		}

		String[] lines = text.split("\n");
		int w = 0;

		for (String line : lines) {
			w = Math.max(w, font.width(line));
		}

		int x = mouseX + 8;
		int y = mouseY - 4 - lines.length * 10;

		g.fill(x - 2, y - 2, x + w + 2, y + lines.length * 10, PANEL);
		g.outline(x - 2, y - 2, w + 4, lines.length * 10, BORDER_HI);

		for (int i = 0; i < lines.length; i++) {
			g.text(font, lines[i], x, y + i * 10, TEXT, false);
		}
	}

	public static boolean contains(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	public static int clamp(int v, int min, int max) {
		return v < min ? min : Math.min(v, max);
	}

	public static float clamp01(float v) {
		return v < 0 ? 0 : Math.min(v, 1);
	}

	public static String formatHex(int argb) {
		return String.format("#%06X", argb & 0xFFFFFF);
	}
}
