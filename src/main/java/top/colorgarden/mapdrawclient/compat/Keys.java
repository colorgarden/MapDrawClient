package top.colorgarden.mapdrawclient.compat;

/**
 * 按键常量（GLFW 数值，MC 内部各版本都用这套编码）。
 *
 * <p>不直接引用 {@code org.lwjgl.glfw.GLFW}：26.3 起 LWJGL 换成了 SDL（没有 lwjgl-glfw），
 * 而这些数值在 MC 的输入系统里是通用的，因此自己声明一份即可跨版本使用。</p>
 */
public final class Keys {
	private Keys() {
	}

	// 字母
	public static final int A = 65;
	public static final int G = 71;
	public static final int H = 72;
	public static final int J = 74;
	public static final int K = 75;
	public static final int L = 76;
	public static final int O = 79;
	public static final int R = 82;
	public static final int S = 83;
	public static final int V = 86;
	public static final int Y = 89;
	public static final int Z = 90;

	// 数字
	public static final int NUM_1 = 49;
	public static final int NUM_2 = 50;
	public static final int NUM_3 = 51;
	public static final int NUM_4 = 52;

	// 功能键
	public static final int F1 = 290;
	public static final int F2 = 291;
	public static final int F3 = 292;
	public static final int F5 = 294;
	public static final int F9 = 348;
	public static final int F11 = 300;

	// 编辑/导航
	public static final int SPACE = 32;
	public static final int ESCAPE = 256;
	public static final int ENTER = 257;
	public static final int BACKSPACE = 259;
	public static final int DELETE = 261;
	public static final int HOME = 268;
	public static final int END = 269;
	public static final int LEFT = 263;
	public static final int RIGHT = 262;

	// 符号
	public static final int COMMA = 44;
	public static final int PERIOD = 46;
	public static final int MINUS = 45;
	public static final int EQUAL = 61;

	// 小键盘
	public static final int KP_ENTER = 335;
	public static final int KP_ADD = 334;
	public static final int KP_SUBTRACT = 333;

	// 修饰键（GLFW mods 位）
	public static final int MOD_SHIFT = 0x0001;
	public static final int MOD_CONTROL = 0x0002;
}