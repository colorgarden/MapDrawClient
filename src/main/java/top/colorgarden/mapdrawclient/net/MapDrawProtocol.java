package top.colorgarden.mapdrawclient.net;

import net.minecraft.resources.Identifier;

/**
 * 与 Paper 服务端 MapDraw 插件约定的协议常量。
 *
 * <p>通道名固定为 {@code mapdraw:main}。第一个字节永远是 PacketID。
 * 所有字段使用 {@link java.io.DataOutputStream} 的语义写出
 * (UTF 为 2 字节长度前缀的修改版 UTF-8，整数为大端)，
 * 因为服务端使用 {@code ByteStreams.newDataInput / DataInputStream} 解析。</p>
 */
public final class MapDrawProtocol {
	private MapDrawProtocol() {
	}

	/** 插件消息通道：mapdraw:main */
	public static final Identifier CHANNEL = top.colorgarden.mapdrawclient.compat.Compat.makeId("mapdraw", "main");

	// ---- 客户端 -> 服务端 ----
	public static final int C2S_DRAW_PIXEL = 0x01;
	public static final int C2S_DRAW_BATCH = 0x02;
	public static final int C2S_UNDO = 0x03;
	public static final int C2S_REDO = 0x04;
	public static final int C2S_PROTECT = 0x05;
	public static final int C2S_DEPROTECT = 0x06;
	public static final int C2S_SET_META = 0x07;
	public static final int C2S_CREATE_CANVAS = 0x08;
	public static final int C2S_SET_TOOL = 0x09;
	public static final int C2S_SET_COLOR = 0x0A;
	public static final int C2S_OPEN_GUI = 0x0B;
	public static final int C2S_REQUEST_CANVAS = 0x0C;

	// ---- 服务端 -> 客户端 ----
	public static final int S2C_RESPONSE = 0x80;
	public static final int S2C_CANVAS_SYNC = 0x81;

	/**
	 * 插件 {@code PacketProtocol} 里定义了但 1.0-SNAPSHOT 从未发送的包，
	 * 客户端只做识别与日志，不再解析（避免协议未定就猜字段）。
	 */
	public static final int S2C_PIXEL_UPDATE = 0x82;

	/** 地图固定 128x128 像素。 */
	public static final int CANVAS_W = 128;
	public static final int CANVAS_H = 128;
	public static final int PIXEL_COUNT = CANVAS_W * CANVAS_H;

	/** 透明像素值：地图颜色字节 0 = 未绘制/透明。 */
	public static final byte TRANSPARENT_COLOR = 0;

	/** 单个 0x02 批量包允许的最大点数，防止超过插件消息上限。 */
	public static final int MAX_BATCH_POINTS = 4096;

	/**
	 * 「尺寸」是逻辑网格分辨率，不是像素区大小。
	 *
	 * <p>插件 {@code DrawingEngine.applyDraw} 的算法是：
	 * {@code gridN = max(1, 128 / size)}，然后把 (x, y) 折算成逻辑格
	 * {@code (x / gridN, y / gridN)}，落笔时把该逻辑格对应的
	 * {@code gridN x gridN} 像素块整块填色。所以任何尺寸下整张 128x128
	 * 都是可画区，size 只决定「一个逻辑像素占几个地图像素」。</p>
	 */
	public static int gridFor(int size) {
		if (size <= 0) {
			return CANVAS_W;
		}

		return Math.max(1, CANVAS_W / Math.min(size, CANVAS_W));
	}

	/** 逻辑格边长乘逻辑格数量：始终等于 128（画布真实像素边）。 */
	public static int usablePixels(int size) {
		return gridFor(size) * Math.min(Math.max(size, 1), CANVAS_W);
	}

	/** 工具类型，与协议字节一一对应。 */
	public enum ToolType {
		PEN(0),
		ERASER(1),
		PAINTBUCKET(2),
		NONE(3);

		private final int id;

		ToolType(int id) {
			this.id = id;
		}

		public byte id() {
			return (byte) this.id;
		}

		public static ToolType byId(int id) {
			for (ToolType t : values()) {
				if (t.id == id) {
					return t;
				}
			}

			return NONE;
		}
	}

	/** 0x07 SET_META 的字段枚举。 */
	public enum MetaField {
		TITLE(0),
		DESCRIPTION(1),
		SIZE(2),
		NO_COPY(3);

		private final int id;

		MetaField(int id) {
			this.id = id;
		}

		public byte id() {
			return (byte) this.id;
		}
	}

	/** 0x0B OPEN_GUI 的界面类型。 */
	public enum GuiType {
		MENU(0),
		PALETTE(1);

		private final int id;

		GuiType(int id) {
			this.id = id;
		}

		public byte id() {
			return (byte) this.id;
		}
	}

	public static String packetName(int id) {
		return switch (id) {
			case C2S_DRAW_PIXEL -> "DRAW_PIXEL(0x01)";
			case C2S_DRAW_BATCH -> "DRAW_BATCH(0x02)";
			case C2S_UNDO -> "UNDO(0x03)";
			case C2S_REDO -> "REDO(0x04)";
			case C2S_PROTECT -> "PROTECT(0x05)";
			case C2S_DEPROTECT -> "DEPROTECT(0x06)";
			case C2S_SET_META -> "SET_META(0x07)";
			case C2S_CREATE_CANVAS -> "CREATE_CANVAS(0x08)";
			case C2S_SET_TOOL -> "SET_TOOL(0x09)";
			case C2S_SET_COLOR -> "SET_COLOR(0x0A)";
			case C2S_OPEN_GUI -> "OPEN_GUI(0x0B)";
			case C2S_REQUEST_CANVAS -> "REQUEST_CANVAS(0x0C)";
			case S2C_RESPONSE -> "S2C_RESPONSE(0x80)";
			case S2C_CANVAS_SYNC -> "CANVAS_DATA_SYNC(0x81)";
			case S2C_PIXEL_UPDATE -> "S2C_PIXEL_UPDATE(0x82, 插件未使用)";
			default -> "UNKNOWN(0x" + Integer.toHexString(id) + ")";
		};
	}
}
