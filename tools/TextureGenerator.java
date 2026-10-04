import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * MapDrawClient 占位贴图生成器 (纯 JDK，无需联网/依赖)。
 *
 * 运行:  java tools/TextureGenerator.java
 * 输出:  src/main/resources/assets/mapdrawclient/textures/gui/*.png
 *        src/main/resources/assets/mapdrawclient/icon.png
 *
 * 每个 8x8 逻辑图案会放大 2 倍得到 16x16 的 GUI 图标。
 * 字符含义: '#' 主色  '*' 强调色  '+' 描边/阴影  '.' 透明
 */
public final class TextureGenerator {
	private static final String OUT_DIR = "src/main/resources/assets/mapdrawclient/textures/gui";
	private static final int MAIN = 0xFFE8E8F0;
	private static final int ACCENT = 0xFF55FFFF;
	private static final int SHADOW = 0xCC101018;

	private static int written;

	public static void main(String[] args) throws IOException {
		File out = new File(OUT_DIR);
		if (!out.exists() && !out.mkdirs()) {
			throw new IOException("无法创建输出目录: " + out.getAbsolutePath());
		}

		// ---- 工具类 ----
		icon2x("tool_pen", new String[]{
				"......+#",
				".....+*#",
				"....+**#",
				"...+**#.",
				"..+**#..",
				".+**#...",
				"+**#....",
				"+##....."
		});
		icon2x("tool_eraser", new String[]{
				"...#####",
				"..+####+",
				".+####+.",
				"+####+..",
				"####+...",
				"###+....",
				"##+.....",
				"+......."
		});
		icon2x("tool_bucket", new String[]{
				"...**...",
				"..****..",
				".*####*.",
				"*######*",
				"*#++++#*",
				"*#++++#*",
				".*####*.",
				"..*++*.."
		});
		icon2x("cursor_brush", new String[]{
				".....##.",
				"....+##.",
				"...+##..",
				"..+##...",
				".+##....",
				"+##.....",
				"#.......",
				"........"
		});

		// ---- 操作类 ----
		icon2x("action_undo", new String[]{
				"..*.....",
				".**.....",
				"***.....",
				".********",
				"..******",
				"........",
				"........",
				"........"
		});
		icon2x("action_redo", new String[]{
				".....*..",
				".....**.",
				".....***",
				"********",
				".*******",
				"........",
				"........",
				"........"
		});
		icon2x("action_sync", new String[]{
				"........",
				".******.",
				"*......*",
				".******.",
				"........",
				".******.",
				"*......*",
				"..****.."
		});
		icon2x("action_lock", new String[]{
				"..####..",
				".#....#.",
				".#....#.",
				"########",
				"#..++..#",
				"#..++..#",
				"########",
				"........"
		});
		icon2x("action_unlock", new String[]{
				"..####..",
				".#....#.",
				".#......",
				"########",
				"#..++..#",
				"#..++..#",
				"########",
				"........"
		});
		icon2x("action_grid", new String[]{
				"########",
				"#..#..##",
				"#..#..##",
				"########",
				"#..#..##",
				"#..#..##",
				"########",
				"........"
		});
		icon2x("action_new", new String[]{
				".#####..",
				".#...##.",
				".#...###",
				".#...*#.",
				".#..***#",
				".#...*#.",
				".#....#.",
				".######."
		});
		icon2x("action_list", new String[]{
				"........",
				"##.####.",
				"........",
				"##.####.",
				"........",
				"##.####.",
				"........",
				"........"
		});
		icon2x("action_menu", new String[]{
				"........",
				"########",
				"........",
				"########",
				"........",
				"########",
				"........",
				"........"
		});
		icon2x("action_close", new String[]{
				"#......#",
				".#....#.",
				"..#..#..",
				"...##...",
				"...##...",
				"..#..#..",
				".#....#.",
				"#......#"
		});
		icon2x("action_check", new String[]{
				"......*#",
				".....**.",
				"...**...",
				"..**....",
				".**.....",
				"**......",
				"*.......",
				"........"
		});
		icon2x("palette", new String[]{
				"..####..",
				".######.",
				"####*###",
				"#*######",
				"#####*##",
				"######+.",
				".######.",
				"..####.."
		});
		icon2x("zoom_in", new String[]{
				"..####..",
				".#....#.",
				"#..**..#",
				"#..**..#",
				"#.**#..#",
				".#....#.",
				"..####.#",
				"........"
		});
		icon2x("zoom_out", new String[]{
				"..####..",
				".#....#.",
				"#......#",
				"#.***..#",
				"#......#",
				".#....#.",
				"..####.#",
				"........"
		});

		// ---- 面板与底纹 ----
		panelBackground(new File(out, "panel_bg.png"));
		checker(new File(out, "checker.png"));
		modIcon(new File("src/main/resources/assets/mapdrawclient/icon.png"));

		System.out.println("已生成 " + written + " 个占位贴图 → " + out.getPath());
	}

	// ------------------------------------------------------------------
	private static void icon2x(String name, String[] art) throws IOException {
		int scale = 2;
		int size = art.length * scale;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < art.length; y++) {
			String row = art[y];

			for (int x = 0; x < art.length; x++) {
				char c = x < row.length() ? row.charAt(x) : '.';
				int color = switch (c) {
					case '#' -> MAIN;
					case '*' -> ACCENT;
					case '+' -> SHADOW;
					default -> 0;
				};

				if (color == 0) {
					continue;
				}

				for (int dy = 0; dy < scale; dy++) {
					for (int dx = 0; dx < scale; dx++) {
						image.setRGB(x * scale + dx, y * scale + dy, color);
					}
				}
			}
		}

		write(image, new File(OUT_DIR, name + ".png"));
	}

	/** 16x16 可平铺的深色面板底。 */
	private static void panelBackground(File file) throws IOException {
		int size = 16;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean edge = x == 0 || y == 0 || x == size - 1 || y == size - 1;
				int color = edge ? 0xFF3A3A46 : 0xF0101014;
				image.setRGB(x, y, color);
			}
		}

		write(image, file);
	}

	/** 16x16 透明棋盘格 (透明像素的底纹)。 */
	private static void checker(File file) throws IOException {
		int size = 16;
		int cell = 8;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean a = ((x / cell) + (y / cell)) % 2 == 0;
				image.setRGB(x, y, a ? 0xFF3C3C44 : 0xFF2A2A30);
			}
		}

		write(image, file);
	}

	/** 128x128 模组图标：画布 + 笔刷 + 调色点。 */
	private static void modIcon(File file) throws IOException {
		int size = 128;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				boolean cell = ((x / 8) + (y / 8)) % 2 == 0;
				image.setRGB(x, y, cell ? 0xFF2A2A30 : 0xFF3C3C44);
			}
		}

		// 边框
		for (int i = 0; i < size; i++) {
			image.setRGB(i, 0, 0xFF101018);
			image.setRGB(i, size - 1, 0xFF101018);
			image.setRGB(0, i, 0xFF101018);
			image.setRGB(size - 1, i, 0xFF101018);
		}

		// 调色板色块
		int[] swatches = {0xFF993333, 0xFF667F33, 0xFF334CB2, 0xFFE5E533, 0xFF7F3FB2, 0xFF4C7F99, 0xFFFFFFFF, 0xFF191919};
		int sw = 12;
		int gap = 3;
		int startX = 8;
		int startY = 8;

		for (int i = 0; i < swatches.length; i++) {
			int sx = startX + (i % 4) * (sw + gap);
			int sy = startY + (i / 4) * (sw + gap);

			for (int y = 0; y < sw; y++) {
				for (int x = 0; x < sw; x++) {
					image.setRGB(sx + x, sy + y, swatches[i]);
				}
			}
		}

		// 斜向笔刷
		for (int i = 0; i < 84; i++) {
			int bx = 24 + i;
			int by = 108 - i;

			for (int t = 0; t < 7; t++) {
				int px = bx + t;
				int py = by + t;

				if (px >= 0 && px < size - 1 && py >= 1 && py < size - 1) {
					image.setRGB(px, py, i > 60 ? 0xFF55FFFF : 0xFFE8E8F0);
				}
			}
		}

		write(image, file);
	}

	private static void write(BufferedImage image, File file) throws IOException {
		File parent = file.getParentFile();

		if (parent != null && !parent.exists() && !parent.mkdirs()) {
			throw new IOException("无法创建目录: " + parent.getAbsolutePath());
		}

		ImageIO.write(image, "PNG", file);
		written++;
		System.out.println("  写出 " + file.getPath());
	}
}
