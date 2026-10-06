package top.colorgarden.mapdrawclient.net;

import top.colorgarden.mapdrawclient.compat.Compat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;

import top.colorgarden.mapdrawclient.MapDrawClient;
import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.GuiType;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.MetaField;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.ToolType;

/**
 * mapdraw:main 通道的收发实现。
 *
 * <p>客户端 -> 服务端：{@code 0x01 ~ 0x0C}
 * <br>服务端 -> 客户端：{@code 0x80} 操作回执、{@code 0x81} 画布全量同步</p>
 *
 * <p>所有负载用 {@link java.io.DataOutputStream} 手工拼装：服务端使用
 * {@code ByteStreams.newDataInput(payload)} 解析，UTF 字符串是 2 字节长度前缀的
 * 修改版 UTF-8，与 Minecraft 的 FriendlyByteBuf#writeUtf (VarInt 前缀) <b>不兼容</b>，
 * 因此这里绝不能直接用 writeUtf。</p>
 */
public final class MapDrawClientNetworking {
	private MapDrawClientNetworking() {
	}

	@FunctionalInterface
	public interface BodyWriter {
		void write(DataOutputStream out) throws IOException;
	}

	@FunctionalInterface
	public interface BodyReader<T> {
		T read(DataInputStream in) throws IOException;
	}

	// ------------------------------------------------------------------
	// 注册
	// ------------------------------------------------------------------
	public static void init() {
		// 双向注册，发送前必须注册 serverboundPlay，接收前必须注册 clientboundPlay
		Compat.registerPayloadC2S();
		Compat.registerPayloadS2C();

		ClientPlayNetworking.registerGlobalReceiver(MapDrawPayload.TYPE, (payload, context) -> {
			final byte[] data = payload.data();
			// 统一切回主线程处理，避免在 Netty 线程里碰游戏状态
			context.client().execute(() -> handleServerPayload(data));
		});
	}

	/** 服务端是否声明可以接收 mapdraw:main (即是否装了 MapDraw 插件)。 */
	public static boolean canSend() {
		try {
			return ClientPlayNetworking.canSend(MapDrawPayload.TYPE);
		} catch (Throwable ignored) {
			return false;
		}
	}

	// ------------------------------------------------------------------
	// 发送：客户端 -> 服务端 (0x01 ~ 0x0C)
	// ------------------------------------------------------------------

	/** 0x01 DRAW_PIXEL —— 单点绘制。 */
	public static void drawPixel(String canvasId, int x, int y, ToolType tool, byte color) {
		send(MapDrawProtocol.C2S_DRAW_PIXEL, out -> {
			out.writeUTF(canvasId);
			out.writeShort(x);
			out.writeShort(y);
			out.writeByte(tool.id());
			out.writeByte(color);
		});
	}

	/** 0x02 DRAW_BATCH —— 拖拽平滑画线，一次发包多个点。 */
	public static void drawBatch(String canvasId, ToolType tool, byte color, List<int[]> points) {
		if (points == null || points.isEmpty()) {
			return;
		}

		// 调用方负责按配置切块，这里只做协议上限保护
		final int sendCount = Math.min(points.size(), MapDrawProtocol.MAX_BATCH_POINTS);
		send(MapDrawProtocol.C2S_DRAW_BATCH, out -> {
			out.writeUTF(canvasId);
			out.writeByte(tool.id());
			out.writeByte(color);
			out.writeShort(sendCount);

			for (int i = 0; i < sendCount; i++) {
				int[] p = points.get(i);
				out.writeShort((short) p[0]);
				out.writeShort((short) p[1]);
			}
		});
	}

	/** 0x03 UNDO —— 撤销。 */
	public static void undo(String canvasId) {
		send(MapDrawProtocol.C2S_UNDO, out -> out.writeUTF(canvasId));
	}

	/** 0x04 REDO —— 重做。 */
	public static void redo(String canvasId) {
		send(MapDrawProtocol.C2S_REDO, out -> out.writeUTF(canvasId));
	}

	/** 0x05 PROTECT —— 锁定保护。 */
	public static void protect(String canvasId) {
		send(MapDrawProtocol.C2S_PROTECT, out -> out.writeUTF(canvasId));
	}

	/** 0x06 DEPROTECT —— 解除保护 (仅作者或管理员)。 */
	public static void deprotect(String canvasId) {
		send(MapDrawProtocol.C2S_DEPROTECT, out -> out.writeUTF(canvasId));
	}

	/** 0x07 SET_META —— 修改标题/描述/尺寸/防拷贝。 */
	public static void setMeta(String canvasId, MetaField field, String value) {
		send(MapDrawProtocol.C2S_SET_META, out -> {
			out.writeUTF(canvasId);
			out.writeByte(field.id());
			out.writeUTF(value == null ? "" : value);
		});
	}

	/** 0x08 CREATE_CANVAS —— 申请创建新画布 (服务端扣费)。 */
	public static void createCanvas(String name, int size) {
		send(MapDrawProtocol.C2S_CREATE_CANVAS, out -> {
			out.writeUTF(name == null ? "" : name);
			out.writeInt(size);
		});
	}

	/** 0x09 SET_TOOL —— 切换手持绘图工具。 */
	public static void setTool(ToolType tool) {
		send(MapDrawProtocol.C2S_SET_TOOL, out -> out.writeByte(tool.id()));
	}

	/** 0x0A SET_COLOR —— 设置画笔颜色。 */
	public static void setColor(int r, int g, int b) {
		send(MapDrawProtocol.C2S_SET_COLOR, out -> {
			out.writeInt(clampChannel(r));
			out.writeInt(clampChannel(g));
			out.writeInt(clampChannel(b));
		});
	}

	/** 0x0B OPEN_GUI —— 让服务端打开它自己的界面 (0=菜单 1=调色板)。 */
	public static void openServerGui(GuiType guiType, String canvasId) {
		send(MapDrawProtocol.C2S_OPEN_GUI, out -> {
			out.writeByte(guiType.id());
			out.writeUTF(canvasId == null ? "" : canvasId);
		});

		// 告诉 CanvasStore：这次原生菜单是我们主动要的，兜底拦截不要把它关掉
		CanvasStore.INSTANCE.expectPluginMenu();
	}

	/** 0x0C REQUEST_CANVAS —— 请求完整画布元数据与 16384 像素。 */
	public static void requestCanvas(String canvasId) {
		if (canvasId == null || canvasId.isEmpty()) {
			return;
		}

		send(MapDrawProtocol.C2S_REQUEST_CANVAS, out -> out.writeUTF(canvasId));
	}

	/**
	 * 落笔发包限速泵（由 {@code CanvasStore} 的每 tick 事件驱动）。
	 *
	 * <p>落笔点先入 {@link DrawSendQueue}，这里按配置配额往外发，
	 * 保证包速率远低于 Paper {@code packet-limiter} 的阈值。</p>
	 */
	public static void pumpDrawQueue() {
		DrawSendQueue.INSTANCE.tick();
	}

	private static int clampChannel(int v) {
		return Math.max(0, Math.min(255, v));
	}

	// ------------------------------------------------------------------
	// 接收：服务端 -> 客户端 (0x80 / 0x81)
	// ------------------------------------------------------------------
	private static void handleServerPayload(byte[] data) {
		MapDrawConfig cfg = MapDrawConfig.get();

		if (cfg.debugPacketLog) {
			MapDrawClient.LOGGER.info("[MapDrawClient] <- {} bytes: {}", data.length, toHex(data, 48));
		}

		if (data.length == 0) {
			return;
		}

		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			int packetId = in.readUnsignedByte();

			switch (packetId) {
				case MapDrawProtocol.S2C_RESPONSE -> handleResponse(in);
			case MapDrawProtocol.S2C_CANVAS_INFO -> handleCanvasInfo(in);
			case MapDrawProtocol.S2C_CONNECTED_MATRIX -> handleConnectedMatrix(in);
				case MapDrawProtocol.S2C_CANVAS_SYNC -> handleCanvasSync(in);
				case MapDrawProtocol.S2C_PIXEL_UPDATE -> MapDrawClient.LOGGER.info(
						"[MapDrawClient] 收到 0x82 S2C_PIXEL_UPDATE（当前插件版本未使用该包），前 32 字节: {}",
						toHex(data, 32));
				default -> MapDrawClient.LOGGER.warn("[MapDrawClient] 未知的服务端包: 0x{}",
						Integer.toHexString(packetId));
			}
		} catch (Exception e) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] 解析服务端包失败: {} (前 48 字节: {})",
					e.toString(), toHex(data, 48));
		}
	}

	/** 0x80 PACKET_RESPONSE: byte originalPacketId, boolean success, String message */
	private static void handleResponse(DataInputStream in) throws IOException {
		int originalPacketId = in.readUnsignedByte();
		boolean success = in.readBoolean();
		String message = in.readUTF();
		MapDrawClient.LOGGER.info("[MapDrawClient] 服务端回执: 包=0x{} 成功={} 消息={}",
				Integer.toHexString(originalPacketId), success, message);
		CanvasStore.INSTANCE.onResponse(originalPacketId, success, message);
	}

	/** 0x81 CANVAS_DATA_SYNC: 画布元数据 + 16384 像素 */
	private static void handleCanvasSync(DataInputStream in) throws IOException {
		CanvasData canvas = CanvasData.read(in);
		CanvasStore.INSTANCE.onSync(canvas);
	}

	// ------------------------------------------------------------------
	// 工具
	// ------------------------------------------------------------------
	private static byte[] build(int packetId, BodyWriter writer) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);

		try (DataOutputStream out = new DataOutputStream(buffer)) {
			out.writeByte(packetId);
			writer.write(out);
			out.flush();
		} catch (IOException e) {
			MapDrawClient.LOGGER.error("[MapDrawClient] 组装 {} 失败", MapDrawProtocol.packetName(packetId), e);
			return null;
		}

		return buffer.toByteArray();
	}

	private static void send(int packetId, BodyWriter writer) {
		byte[] body = build(packetId, writer);

		if (body == null) {
			return;
		}

		MapDrawConfig cfg = MapDrawConfig.get();

		if (cfg.debugPacketLog) {
			MapDrawClient.LOGGER.info("[MapDrawClient] -> {} ({} bytes)", MapDrawProtocol.packetName(packetId), body.length);
		}

		if (!canSend()) {
			CanvasStore.INSTANCE.setStatus("服务器未声明 mapdraw:main 通道，请确认服务端已安装 MapDraw 插件", 0xFFFF6666);
			return;
		}

		try {
			ClientPlayNetworking.send(new MapDrawPayload(body));
		} catch (Throwable t) {
			CanvasStore.INSTANCE.setStatus("发送失败: " + t.getClass().getSimpleName(), 0xFFFF6666);
			MapDrawClient.LOGGER.warn("[MapDrawClient] 发送 {} 失败", MapDrawProtocol.packetName(packetId), t);
		}
	}

	/** 供界面层复用：按需在客户端解析一个响应包 (一般用不到)。 */
	public static <T> T readBody(byte[] data, int expectedPacketId, BodyReader<T> reader) throws IOException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			int packetId = in.readUnsignedByte();

			if (packetId != expectedPacketId) {
				return null;
			}

			return reader.read(in);
		}
	}

	private static String toHex(byte[] data, int max) {
		StringBuilder sb = new StringBuilder();
		int n = Math.min(data.length, max);

		for (int i = 0; i < n; i++) {
			if (i > 0) {
				sb.append(' ');
			}

			sb.append(String.format("%02X", data[i]));
		}

		if (data.length > n) {
			sb.append(" ...");
		}

		return sb.toString();
	}

	/** 便捷方法：当前主菜单是否已打开 (给界面层用)。 */
	public static Minecraft client() {
		return Minecraft.getInstance();
	}

	/** 0x0D 设置是否启用服务端箱子菜单（我们用自己的界面 → 发 false）。 */
	public static void setChestGui(boolean enabled) {
		send(MapDrawProtocol.C2S_SET_CHEST_GUI, out -> out.writeBoolean(enabled));
	}

	/** 0x10 上传图片（24KB 分片，照插件 README 的示例实现）。 */
	public static void uploadImage(byte[] bytes, String algorithm, int cols, int rows) {
		if (bytes == null || bytes.length == 0) {
			return;
		}

		String uploadId = java.util.UUID.randomUUID().toString();
		int chunkSize = 24576;
		int totalChunks = (int) Math.ceil((double) bytes.length / chunkSize);

		for (int i = 0; i < totalChunks; i++) {
			int start = i * chunkSize;
			int end = Math.min(bytes.length, start + chunkSize);
			byte[] chunk = java.util.Arrays.copyOfRange(bytes, start, end);
			int index = i;

			send(MapDrawProtocol.C2S_UPLOAD_CHUNK, out -> {
				out.writeUTF(uploadId);
				out.writeInt(index);
				out.writeInt(totalChunks);
				out.writeInt(bytes.length);
				out.writeUTF(algorithm == null ? "dither" : algorithm);
				out.writeShort(cols);
				out.writeShort(rows);
				out.writeInt(chunk.length);
				out.write(chunk);
			});
		}

		top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info(
				"[MapDrawClient] 已发送图片上传：{} 字节 / {} 片，算法={}，{}x{}",
				bytes.length, totalChunks, algorithm, cols, rows);
	}

	/** 0x0E 轻量查询画布属性。 */
	public static void requestCanvasInfo(String canvasId) {
		send(MapDrawProtocol.C2S_REQUEST_CANVAS_INFO, out -> out.writeUTF(canvasId == null ? "" : canvasId));
	}

	/** 0x11 查询相连画布拓扑。 */
	public static void queryConnected(int entityId, int maxRadius) {
		send(MapDrawProtocol.C2S_QUERY_CONNECTED, out -> {
			out.writeInt(entityId);
			out.writeByte(Math.max(1, Math.min(10, maxRadius)));
		});
	}

	/** 0x12 大画板全局像素绘制。 */
	public static void drawGridPixel(int baseEntityId, int globalX, int globalY, MapDrawProtocol.ToolType tool, byte color) {
		send(MapDrawProtocol.C2S_DRAW_GRID_PIXEL, out -> {
			out.writeInt(baseEntityId);
			out.writeInt(globalX);
			out.writeInt(globalY);
			out.writeByte(tool == MapDrawProtocol.ToolType.ERASER ? 1 : tool == MapDrawProtocol.ToolType.PAINTBUCKET ? 2 : 0);
			out.writeByte(color);
		});
	}

	/** 0x83：轻量画布属性（含是否 GIF 动图、帧率、帧数）。 */
	private static void handleCanvasInfo(java.io.DataInputStream in) throws java.io.IOException {
		String id = in.readUTF();
		int mapId = in.readInt();
		String name = in.readUTF();
		String title = in.readUTF();
		String desc = in.readUTF();
		int size = in.readInt();
		boolean protectedCanvas = in.readBoolean();
		boolean noCopy = in.readBoolean();
		String creator = in.readUTF();
		boolean animated = in.readBoolean();
		int fps = in.readInt();
		int frameCount = in.readInt();
		ServerCanvasInfo.setInfo(id, title, size, animated, fps, frameCount);
		top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info(
				"[MapDrawClient] 画布属性: {} 标题={} 尺寸={} 动图={} fps={} 帧数={}",
				id, title, size, animated, fps, frameCount);
	}

	/** 0x84：相连画布矩阵（多联大图拓扑）。 */
	private static void handleConnectedMatrix(java.io.DataInputStream in) throws java.io.IOException {
		int cols = in.readInt();
		int rows = in.readInt();
		int totalW = in.readInt();
		int totalH = in.readInt();
		int nodeCount = in.readInt();

		java.util.List<Object[]> nodes = new java.util.ArrayList<>();

		for (int i = 0; i < nodeCount; i++) {
			short gridCol = in.readShort();
			short gridRow = in.readShort();
			int entityId = in.readInt();
			String canvasId = in.readUTF();
			int mapId = in.readInt();
			boolean prot = in.readBoolean();
			boolean anim = in.readBoolean();
			nodes.add(new Object[]{(int) gridCol, (int) gridRow, entityId, canvasId, mapId});
		}

		ServerCanvasInfo.setNodes(nodes);
		ServerCanvasInfo.setMatrix(cols, rows, nodeCount, totalW, totalH);
		top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info(
				"[MapDrawClient] 相连画布矩阵: {}x{} 共 {} 格，总像素 {}x{}", cols, rows, nodeCount, totalW, totalH);
	}
}
