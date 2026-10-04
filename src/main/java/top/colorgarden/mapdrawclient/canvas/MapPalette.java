package top.colorgarden.mapdrawclient.canvas;

import java.util.Locale;

/**
 * Minecraft 地图调色板 (MapPalette / MapColor) 的客户端副本。
 *
 * <p>像素值是一个字节：{@code id = 基色索引 * 4 + 明暗档}，
 * 明暗倍率为 {@code {180, 220, 255, 135} / 255}，共 61 个基色 x 4 档 = 244 色。
 * 例如 {@code 114 = 28*4+2 → COLOR_RED(0x993333)}，{@code 70 = 17*4+2 → COLOR_LIGHT_BLUE(0x6699D8)}。</p>
 *
 * <p>{@code 0} 表示透明 (原版地图未绘制像素)。</p>
 */
public final class MapPalette {
	private MapPalette() {
	}

	/** 明暗档倍率 (x255)。 */
	private static final int[] SHADE_MUL = {180, 220, 255, 135};

	private static final String[] SHADE_NAME = {"深", "中", "亮", "暗"};

	/** 61 个基色名。 */
	private static final String[] BASE_NAMES = {
			"透明", "草绿", "沙黄", "羊毛白", "火红", "冰蓝", "金属灰", "植物绿",
			"雪白", "陶土蓝灰", "泥土棕", "石头灰", "水蓝", "木头棕", "石英白", "橙色",
			"品红", "淡蓝", "黄", "淡绿", "粉", "深灰", "淡灰", "青",
			"紫", "蓝", "棕", "绿", "红", "黑", "金", "钻石青",
			"青金石蓝", "翠绿", "灰化土", "地狱红", "陶白", "陶橙", "陶品红", "陶淡蓝",
			"陶黄", "陶淡绿", "陶粉", "陶深棕", "陶淡灰", "陶青灰", "陶紫", "陶蓝",
			"陶棕", "陶绿", "陶红", "陶黑", "绯红菌岩", "绯红菌柄", "绯红菌核", "诡异菌岩",
			"诡异菌柄", "诡异菌核", "诡异疣块", "深板岩", "粗铁"
	};

	/** 61 个基色的 RGB。 */
	private static final int[] BASE_RGB = {
			0x000000, 0x7FB238, 0xF7E9A3, 0xC7C7C7, 0xFF0000, 0xA0A0FF, 0xA7A7A7, 0x007C00,
			0xFFFFFF, 0xA4A8B8, 0x976D4D, 0x707070, 0x4040FF, 0x8F7748, 0xFFFCF5, 0xD87F33,
			0xB24CD8, 0x6699D8, 0xE5E533, 0x7FCC19, 0xF27FA5, 0x4C4C4C, 0x999999, 0x4C7F99,
			0x7F3FB2, 0x334CB2, 0x664C33, 0x667F33, 0x993333, 0x191919, 0xFAEE4D, 0x5CDBD5,
			0x4A80FF, 0x00D93A, 0x815631, 0x700200, 0xD1B1A1, 0x9F5224, 0x95576C, 0x706C8A,
			0xBA8524, 0x677535, 0xA24D4E, 0x5A3F37, 0x8B6D64, 0x575C5C, 0x7A4958, 0x4C3E5C,
			0x4C3223, 0x4C522A, 0x8E3C2E, 0x251610, 0xBD3031, 0x943F61, 0x5C191D, 0x167E86,
			0x3A8E8C, 0x562C3E, 0x14B485, 0x646464, 0xD8AF93
	};

	public static final int BASE_COUNT = BASE_RGB.length;
	public static final int COLOR_COUNT = BASE_COUNT * 4;

	private static final int[] ARGB_CACHE = new int[COLOR_COUNT];

	static {
		for (int i = 0; i < COLOR_COUNT; i++) {
			if (i == 0) {
				ARGB_CACHE[i] = 0x00000000; // 透明
				continue;
			}

			int base = BASE_RGB[i / 4];
			int mul = SHADE_MUL[i & 3];
			int r = ((base >> 16) & 0xFF) * mul / 255;
			int g = ((base >> 8) & 0xFF) * mul / 255;
			int b = (base & 0xFF) * mul / 255;
			ARGB_CACHE[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
		}
	}

	/** 内置 16 色快捷调色板 (亮档 shade=2)，另加透明。 */
	public static final byte[] QUICK_16 = {
			118, // 黑
			114, // 红
			110, // 绿
			102, // 蓝
			106, // 棕
			94,  // 青
			90,  // 淡灰
			86,  // 深灰
			82,  // 粉
			78,  // 淡绿
			74,  // 黄
			70,  // 淡蓝
			66,  // 品红
			62,  // 橙
			58,  // 白 (石英)
			98   // 紫
	};

	public static final byte TRANSPARENT = 0;

	public static boolean isValid(byte id) {
		int v = id & 0xFF;
		return v < COLOR_COUNT;
	}

	public static boolean isTransparent(byte id) {
		return id == 0;
	}

	/** 取像素字节对应的 ARGB；0 为全透明。 */
	public static int argb(byte id) {
		int v = id & 0xFF;

		if (v >= COLOR_COUNT) {
			return 0xFFFF00FF;
		}

		return ARGB_CACHE[v];
	}

	public static int rgb(byte id) {
		return argb(id) & 0x00FFFFFF;
	}

	public static int baseIndex(byte id) {
		return (id & 0xFF) / 4;
	}

	public static int shadeIndex(byte id) {
		return (id & 0xFF) & 3;
	}

	/** 人类可读名称，例如 “红 亮”。 */
	public static String name(byte id) {
		int v = id & 0xFF;

		if (v == 0) {
			return "透明";
		}

		if (v >= COLOR_COUNT) {
			return "未知(" + v + ")";
		}

		return BASE_NAMES[v / 4] + " " + SHADE_NAME[v & 3];
	}

	/** 找出与给定 ARGB 最接近的地图颜色字节 (加权 RGB 距离)。 */
	public static byte nearest(int argb) {
		if ((argb >>> 24) == 0) {
			return TRANSPARENT;
		}

		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int best = 0;
		int bestDist = Integer.MAX_VALUE;

		for (int i = 1; i < COLOR_COUNT; i++) {
			int c = ARGB_CACHE[i];
			int dr = r - ((c >> 16) & 0xFF);
			int dg = g - ((c >> 8) & 0xFF);
			int db = b - (c & 0xFF);
			int dist = 30 * dr * dr + 59 * dg * dg + 11 * db * db;

			if (dist < bestDist) {
				bestDist = dist;
				best = i;
			}
		}

		return (byte) best;
	}

	/** 解析 {@code #RRGGBB} / {@code RRGGBB} / 英文色名 / {@code &a} 颜色代码，失败返回 -1。 */
	public static int parseColor(String input) {
		if (input == null) {
			return -1;
		}

		String s = input.trim().toLowerCase(Locale.ROOT);

		if (s.isEmpty()) {
			return -1;
		}

		if (s.charAt(0) == '&' && s.length() >= 2) {
			return MC_CODE_COLORS[indexOfCode(s.charAt(1))];
		}

		if (s.charAt(0) == '#') {
			s = s.substring(1);
		}

		if (s.length() == 6 && isHex(s)) {
			return 0xFF000000 | Integer.parseInt(s, 16);
		}

		if (s.length() == 3 && isHex(s)) {
			int r = Integer.parseInt(s.substring(0, 1), 16) * 17;
			int g = Integer.parseInt(s.substring(1, 2), 16) * 17;
			int b = Integer.parseInt(s.substring(2, 3), 16) * 17;
			return 0xFF000000 | (r << 16) | (g << 8) | b;
		}

		Integer named = NAME_COLORS(s);
		return named == null ? -1 : 0xFF000000 | named;
	}

	/** 解析后直接映射到最近的地图颜色字节。 */
	public static byte parseToMapColor(String input, byte fallback) {
		int argb = parseColor(input);
		return argb < 0 ? fallback : nearest(argb);
	}

	public static String toHex(int argb) {
		return String.format("#%06X", argb & 0xFFFFFF);
	}

	private static final int[] MC_CODE_COLORS = {
			0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
	};

	private static int indexOfCode(char c) {
		String codes = "0123456789abcdef";
		int idx = codes.indexOf(c);
		return idx < 0 ? 15 : idx;
	}

	private static boolean isHex(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);

			if (Character.digit(c, 16) < 0) {
				return false;
			}
		}

		return true;
	}

	private static Integer NAME_COLORS(String s) {
		return switch (s) {
			case "black" -> 0x000000;
			case "dark_blue", "navy" -> 0x0000AA;
			case "dark_green", "green" -> 0x00AA00;
			case "dark_aqua", "teal" -> 0x00AAAA;
			case "dark_red", "red", "maroon" -> 0xAA0000;
			case "dark_purple", "purple" -> 0xAA00AA;
			case "gold", "orange" -> 0xFFAA00;
			case "gray", "grey", "silver" -> 0xAAAAAA;
			case "dark_gray", "dark_grey" -> 0x555555;
			case "blue" -> 0x5555FF;
			case "lime", "light_green" -> 0x55FF55;
			case "aqua", "cyan", "light_blue" -> 0x55FFFF;
			case "light_red", "pink", "magenta" -> 0xFF5555;
			case "light_purple", "light_magenta" -> 0xFF55FF;
			case "yellow" -> 0xFFFF55;
			case "white" -> 0xFFFFFF;
			case "brown" -> 0x8B5A2B;
			default -> null;
		};
	}
}
