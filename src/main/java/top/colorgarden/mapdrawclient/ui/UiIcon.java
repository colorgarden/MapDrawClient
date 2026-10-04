package top.colorgarden.mapdrawclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 矢量图标：全部用 fill 画出来，不依赖任何贴图，因此缺素材也能正常显示。
 *
 * <p>若之后想换成美术素材，只需在 {@link #draw} 里把对应分支换成
 * {@code graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, size, size, 16, 16)} 即可，
 * 贴图规格见项目根目录 ASSETS.md。</p>
 */
public enum UiIcon {
	NONE,
	PEN,
	ERASER,
	BUCKET,
	UNDO,
	REDO,
	SYNC,
	LOCK,
	UNLOCK,
	GRID,
	PLUS,
	MINUS,
	LIST,
	MENU,
	PALETTE,
	CLOSE,
	NEW,
	CHECK,
	BACK;

	/** 逻辑分辨率 8x8，实际尺寸由调用方给出。 */
	public void draw(GuiGraphicsExtractor g, int x, int y, int size, int argb) {
		if (this == NONE || size <= 0) {
			return;
		}

		int s = Math.max(1, size / 8);
		int dark = 0xFF000000 | ((argb & 0xFEFEFE) >> 1);

		switch (this) {
			case PEN -> {
				// 斜向笔身 + 笔尖
				for (int i = 0; i < 6; i++) {
					px(g, x, y, s, 1 + i, 6 - i, 2, argb);
				}

				px(g, x, y, s, 6, 0, 2, dark);
				px(g, x, y, s, 7, 1, 1, argb);
				px(g, x, y, s, 1, 7, 1, argb);
			}
			case ERASER -> {
				px(g, x, y, s, 0, 2, 6, argb);
				px(g, x, y, s, 0, 3, 6, dark);
				px(g, x, y, s, 0, 4, 6, argb);
				px(g, x, y, s, 1, 5, 4, dark);
				px(g, x, y, s, 2, 6, 2, argb);
			}
			case BUCKET -> {
				px(g, x, y, s, 3, 0, 2, argb);
				px(g, x, y, s, 2, 1, 4, argb);
				px(g, x, y, s, 1, 2, 6, argb);
				px(g, x, y, s, 1, 3, 6, dark);
				px(g, x, y, s, 1, 4, 6, argb);
				px(g, x, y, s, 2, 5, 4, argb);
				px(g, x, y, s, 3, 6, 2, dark);
			}
			case UNDO -> {
				px(g, x, y, s, 1, 1, 1, argb);
				px(g, x, y, s, 2, 2, 1, argb);
				px(g, x, y, s, 3, 3, 1, argb);
				px(g, x, y, s, 2, 4, 1, argb);
				px(g, x, y, s, 1, 5, 1, argb);
				px(g, x, y, s, 0, 2, 1, argb);
				px(g, x, y, s, 0, 3, 1, argb);
				px(g, x, y, s, 0, 4, 1, argb);
				px(g, x, y, s, 4, 4, 4, argb);
				px(g, x, y, s, 4, 5, 4, argb);
			}
			case REDO -> {
				px(g, x, y, s, 6, 1, 1, argb);
				px(g, x, y, s, 5, 2, 1, argb);
				px(g, x, y, s, 4, 3, 1, argb);
				px(g, x, y, s, 5, 4, 1, argb);
				px(g, x, y, s, 6, 5, 1, argb);
				px(g, x, y, s, 7, 2, 1, argb);
				px(g, x, y, s, 7, 3, 1, argb);
				px(g, x, y, s, 7, 4, 1, argb);
				px(g, x, y, s, 0, 4, 4, argb);
				px(g, x, y, s, 0, 5, 4, argb);
			}
			case SYNC -> {
				px(g, x, y, s, 1, 2, 6, argb);
				px(g, x, y, s, 0, 3, 2, argb);
				px(g, x, y, s, 1, 5, 6, argb);
				px(g, x, y, s, 6, 4, 2, argb);
			}
			case LOCK, UNLOCK -> {
				px(g, x, y, s, 2, 0, 4, argb);
				px(g, x, y, s, 1, 1, 1, argb);
				px(g, x, y, s, 6, 1, 1, this == LOCK ? argb : -1);
				px(g, x, y, s, 0, 3, 8, argb);
				px(g, x, y, s, 0, 5, 8, argb);
				px(g, x, y, s, 0, 4, 1, dark);
				px(g, x, y, s, 7, 4, 1, dark);
				px(g, x, y, s, 3, 6, 2, dark);
			}
			case GRID -> {
				// 外框四条边 + 中间十字
				px(g, x, y, s, 0, 0, 8, argb);
				px(g, x, y, s, 0, 7, 8, argb);
				px(g, x, y, s, 0, 1, 1, argb);
				px(g, x, y, s, 0, 2, 1, argb);
				px(g, x, y, s, 0, 3, 1, argb);
				px(g, x, y, s, 0, 4, 1, argb);
				px(g, x, y, s, 0, 5, 1, argb);
				px(g, x, y, s, 0, 6, 1, argb);
				px(g, x, y, s, 7, 1, 1, argb);
				px(g, x, y, s, 7, 2, 1, argb);
				px(g, x, y, s, 7, 3, 1, argb);
				px(g, x, y, s, 7, 4, 1, argb);
				px(g, x, y, s, 7, 5, 1, argb);
				px(g, x, y, s, 7, 6, 1, argb);
				px(g, x, y, s, 1, 3, 6, dark);
				px(g, x, y, s, 3, 1, 2, dark);
				px(g, x, y, s, 3, 4, 2, dark);
				px(g, x, y, s, 3, 5, 2, dark);
				px(g, x, y, s, 3, 6, 2, dark);
			}
			case PLUS -> {
				px(g, x, y, s, 3, 1, 2, argb);
				px(g, x, y, s, 3, 6, 2, argb);
				px(g, x, y, s, 1, 3, 6, argb);
				px(g, x, y, s, 1, 4, 6, argb);
			}
			case MINUS -> {
				px(g, x, y, s, 1, 3, 6, argb);
				px(g, x, y, s, 1, 4, 6, argb);
			}
			case LIST -> {
				for (int i = 0; i < 3; i++) {
					px(g, x, y, s, 0, 1 + i * 3, 1, argb);
					px(g, x, y, s, 2, 1 + i * 3, 6, argb);
				}
			}
			case MENU -> {
				for (int i = 0; i < 3; i++) {
					px(g, x, y, s, 0, 1 + i * 3, 8, argb);
				}
			}
			case PALETTE -> {
				px(g, x, y, s, 2, 0, 4, argb);
				px(g, x, y, s, 1, 1, 6, argb);
				px(g, x, y, s, 0, 2, 8, argb);
				px(g, x, y, s, 0, 3, 8, dark);
				px(g, x, y, s, 1, 4, 6, argb);
				px(g, x, y, s, 2, 5, 4, argb);
				px(g, x, y, s, 2, 2, 1, 0xFFFFFFFF);
				px(g, x, y, s, 5, 3, 1, 0xFFFFFFFF);
			}
			case CLOSE -> {
				for (int i = 0; i < 6; i++) {
					px(g, x, y, s, 1 + i, 1 + i, 1, argb);
					px(g, x, y, s, 6 - i, 1 + i, 1, argb);
				}
			}
			case NEW -> {
				px(g, x, y, s, 1, 0, 5, argb);
				px(g, x, y, s, 1, 1, 1, argb);
				px(g, x, y, s, 5, 1, 1, argb);
				px(g, x, y, s, 6, 2, 1, argb);
				px(g, x, y, s, 1, 2, 1, argb);
				px(g, x, y, s, 1, 3, 1, argb);
				px(g, x, y, s, 1, 4, 1, argb);
				px(g, x, y, s, 1, 5, 1, argb);
				px(g, x, y, s, 1, 6, 6, argb);
				px(g, x, y, s, 4, 3, 1, dark);
				px(g, x, y, s, 5, 3, 2, dark);
				px(g, x, y, s, 4, 4, 3, dark);
			}
			case CHECK -> {
				// 短下撇 + 长上提，组成勾
				px(g, x, y, s, 1, 3, 1, argb);
				px(g, x, y, s, 2, 4, 1, argb);
				px(g, x, y, s, 3, 5, 1, argb);
				px(g, x, y, s, 3, 6, 1, argb);
				px(g, x, y, s, 4, 5, 1, argb);
				px(g, x, y, s, 5, 4, 1, argb);
				px(g, x, y, s, 6, 3, 1, argb);
				px(g, x, y, s, 7, 2, 1, argb);
			}
			case BACK -> {
				// 左箭头：三角头 + 横杠
				px(g, x, y, s, 3, 0, 1, argb);
				px(g, x, y, s, 2, 1, 1, argb);
				px(g, x, y, s, 1, 2, 1, argb);
				px(g, x, y, s, 0, 3, 7, argb);
				px(g, x, y, s, 1, 4, 1, argb);
				px(g, x, y, s, 2, 5, 1, argb);
				px(g, x, y, s, 3, 6, 1, argb);
			}
			default -> {
			}
		}
	}

	/** 画一个「逻辑像素」。 */
	private static void px(GuiGraphicsExtractor g, int x, int y, int s, int pxX, int pxY, int pxW, int argb) {
		if (argb == -1 || pxW <= 0) {
			return;
		}

		g.fill(x + pxX * s, y + pxY * s, x + (pxX + pxW) * s, y + (pxY + 1) * s, argb);
	}
}
