package top.colorgarden.mapdrawclient.canvas;

import java.io.DataInputStream;
import java.io.IOException;

import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 客户端持有的画布数据 (0x81 CANVAS_DATA_SYNC 的内存副本)。
 *
 * <p>{@link #read(DataInputStream)} 的顺序必须与 Paper 插件写出顺序严格一致：
 * id, mapId, name, title, desc, size, protected, noCopy, creator, animated,
 * pixelLen, pixels[]。</p>
 */
public final class CanvasData {
	private final String id;
	private int mapId;
	private String name = "";
	private String title = "";
	private String description = "";
	private String creator = "";
	private int size = MapDrawProtocol.CANVAS_W;
	private boolean protectedCanvas;
	private boolean noCopy;
	private boolean animated;

	private final byte[] pixels = new byte[MapDrawProtocol.PIXEL_COUNT];

	private long syncedAt = System.currentTimeMillis();
	private int localEdits;

	public CanvasData(String id) {
		this.id = id == null ? "" : id;
	}

	/** 解析 0x81 的负载 (PacketID 已被读取)。 */
	public static CanvasData read(DataInputStream in) throws IOException {
		String id = in.readUTF();
		CanvasData canvas = new CanvasData(id);
		canvas.mapId = in.readInt();
		canvas.name = in.readUTF();
		canvas.title = in.readUTF();
		canvas.description = in.readUTF();
		canvas.size = in.readInt();
		canvas.protectedCanvas = in.readBoolean();
		canvas.noCopy = in.readBoolean();
		canvas.creator = in.readUTF();
		canvas.animated = in.readBoolean();
		// 新版插件（1.0.0+）在 animated 之后多了 fps 与 frameCount（各 4 字节，这里消费掉）
		in.readInt();   // 新版插件的 fps（消费掉，暂不使用）
		in.readInt();   // 新版插件的 frameCount（消费掉，暂不使用）

		int pixelLen = in.readInt();
		int wanted = Math.max(0, Math.min(pixelLen, MapDrawProtocol.PIXEL_COUNT));
		in.readFully(canvas.pixels, 0, wanted);

		// 防御：若服务端给出更多像素，跳过剩余部分
		for (int remaining = pixelLen - wanted; remaining > 0; ) {
			long skipped = in.skip(remaining);

			if (skipped <= 0) {
				break;
			}

			remaining -= (int) skipped;
		}

		if (canvas.size != 16 && canvas.size != 32 && canvas.size != 64 && canvas.size != 128) {
			canvas.size = MapDrawProtocol.CANVAS_W;
		}

		return canvas;
	}

	// ---- 像素 ----
	public byte pixel(int x, int y) {
		if (x < 0 || y < 0 || x >= MapDrawProtocol.CANVAS_W || y >= MapDrawProtocol.CANVAS_H) {
			return 0;
		}

		return this.pixels[y * MapDrawProtocol.CANVAS_W + x];
	}

	public void setPixel(int x, int y, byte value) {
		if (x < 0 || y < 0 || x >= MapDrawProtocol.CANVAS_W || y >= MapDrawProtocol.CANVAS_H) {
			return;
		}

		this.pixels[y * MapDrawProtocol.CANVAS_W + x] = value;
	}

	public byte[] pixels() {
		return this.pixels;
	}

	/**
	 * 该坐标是否可画。
	 *
	 * <p><b>整张 128x128 像素区都可以画</b>：插件 {@code DrawingEngine.applyDraw}
	 * 会把 (x, y) 折算成逻辑格 {@code (x / gridN, y / gridN)}（gridN = 128 / size），
	 * 只要逻辑格落在 {@code [0, size)} 内就成立，对 128x128 内的任何坐标都成立。
	 * 之前这里按 {@code x < size} 判断，等于把 16x16 的画布当成只有左上角
	 * 16x16 像素可画，于是画板看起来「没填满 / 一画就是几块巨大的色块」。</p>
	 */
	public boolean editable(int x, int y) {
		return x >= 0 && y >= 0 && x < MapDrawProtocol.CANVAS_W && y < MapDrawProtocol.CANVAS_H;
	}

	/** 逻辑格边长（一个逻辑像素 = gridN x gridN 个地图像素）。 */
	public int gridN() {
		return MapDrawProtocol.gridFor(this.size);
	}

	/** 逻辑分辨率（size 个逻辑像素）。 */
	public int logicalSize() {
		return Math.min(Math.max(this.size, 1), MapDrawProtocol.CANVAS_W);
	}

	/** 地图像素 -> 逻辑格 X。 */
	public int logicalX(int x) {
		return Math.floorDiv(x, this.gridN());
	}

	/** 地图像素 -> 逻辑格 Y。 */
	public int logicalY(int y) {
		return Math.floorDiv(y, this.gridN());
	}

	/** 对齐到逻辑格左上角（服务端按格落笔，客户端按格吸附才不会「看着画一格、实际盖三格」）。 */
	public int snapX(int x) {
		int g = this.gridN();
		return Math.floorDiv(Math.max(0, x), g) * g;
	}

	public int snapY(int y) {
		int g = this.gridN();
		return Math.floorDiv(Math.max(0, y), g) * g;
	}

	/**
	 * 整张画布是否被同一种颜色铺满。
	 *
	 * @return 该颜色字节（非 0），不是「纯色铺满」时返回 -1
	 */
	public int uniformFill() {
		byte first = this.pixels[0];

		for (byte b : this.pixels) {
			if (b != first) {
				return -1;
			}
		}

		return first == 0 ? -1 : (first & 0xFF);
	}

	/** 是否允许本地编辑 (保护模式下不允许)。 */
	public boolean canEditLocally(String selfName) {
		if (!this.protectedCanvas) {
			return true;
		}

		return selfName != null && !selfName.isEmpty() && selfName.equals(this.creator);
	}

	public void markSynced() {
		this.syncedAt = System.currentTimeMillis();
		this.localEdits = 0;
	}

	public void markLocalEdit() {
		this.localEdits++;
	}

	public int localEdits() {
		return this.localEdits;
	}

	public long syncedAt() {
		return this.syncedAt;
	}

	// ---- 字段 ----
	public String id() {
		return this.id;
	}

	public int mapId() {
		return this.mapId;
	}

	public void setMapId(int mapId) {
		this.mapId = mapId;
	}

	public String name() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name == null ? "" : name;
	}

	public String title() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title == null ? "" : title;
	}

	public String description() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description == null ? "" : description;
	}

	public String creator() {
		return this.creator;
	}

	public void setCreator(String creator) {
		this.creator = creator == null ? "" : creator;
	}

	public int size() {
		return this.size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	/** 是否只增不减 (已填色时服务端禁止缩小，这里按“已有非透明像素”近似判断)。 */
	public boolean hasPaint() {
		for (byte b : this.pixels) {
			if (b != 0) {
				return true;
			}
		}

		return false;
	}

	public boolean isProtected() {
		return this.protectedCanvas;
	}

	public void setProtected(boolean value) {
		this.protectedCanvas = value;
	}

	public boolean noCopy() {
		return this.noCopy;
	}

	public void setNoCopy(boolean value) {
		this.noCopy = value;
	}

	public boolean animated() {
		return this.animated;
	}

	public void setAnimated(boolean value) {
		this.animated = value;
	}

	public String displayName() {
		if (!this.title.isEmpty()) {
			return this.title;
		}

		if (!this.name.isEmpty()) {
			return this.name;
		}

		return this.id.isEmpty() ? "(未选择画布)" : this.id;
	}
}
