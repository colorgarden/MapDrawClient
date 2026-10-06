package top.colorgarden.mapdrawclient.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol.ToolType;

/**
 * 落笔发包限速队列（纯客户端）。
 *
 * <p><b>为什么需要它：</b>Paper 有 {@code packet-limiter}（默认 7 秒滑动窗口内平均
 * 超过 500 包/秒直接 KICK：「由于超出数据包速率限制而被踢出游戏」）。大笔刷 + 快速拖拽
 * 时，一次 {@code paintPixel} 就可能产生上百个格子，如果一帧里全发出去，
 * 包数会瞬间上千 —— 必被踢。</p>
 *
 * <p>所以落笔只入队，由 {@code CanvasStore} 的每 tick 泵按配额发送：
 * 默认 {@code maxPacketsPerTick = 4}、{@code maxPointsPerTick = 2048}
 * （≈ 80 包/秒、40k 点/秒），单个包最多 {@code batchFlushPoints = 512} 个点。
 * 泵跑在 {@code CanvasStore} 的 tick 上，所以即使画板被关掉，积压的点也会发完。</p>
 */
public final class DrawSendQueue {
	public static final DrawSendQueue INSTANCE = new DrawSendQueue();

	/** 一段「同一工具 + 同一颜色」的点。换色/换工具会自动开新段，避免旧积压用错颜色。 */
	private static final class Segment {
		private final ToolType tool;
		private final byte color;
		private final Deque<int[]> points = new ArrayDeque<>();

		private Segment(ToolType tool, byte color) {
			this.tool = tool;
			this.color = color;
		}
	}

	private final Deque<Segment> segments = new ArrayDeque<>();
	private String canvasId = "";
	/** 这一队是否已经发过包（决定首个单点用 0x01 还是 0x02）。 */
	private boolean sentAny;
	private int diagFrames;
	private boolean throttled;

	private DrawSendQueue() {
	}

	/** 开始一次新操作（按下鼠标时调用）。 */
	public void begin(String canvasId) {
		this.canvasId = canvasId == null ? "" : canvasId;
		this.sentAny = false;
		this.throttled = false;
	}

	/**
	 * 入队一个逻辑格。
	 *
	 * @return false = 积压过多，本次没有入队（调用方应提示用户放慢）
	 */
	public boolean enqueue(String canvasId, ToolType tool, byte color, int x, int y) {
		if (!this.canvasId.equals(canvasId == null ? "" : canvasId)) {
			this.begin(canvasId);
			this.segments.clear();
		}

		int limit = Math.max(512, MapDrawConfig.get().maxPendingPoints);

		if (this.size() >= limit) {
			this.throttled = true;
			return false;
		}

		Segment last = this.segments.peekLast();

		if (last == null || last.tool != tool || last.color != color) {
			last = new Segment(tool, color);
			this.segments.addLast(last);
		}

		last.points.addLast(new int[]{x, y});
		return true;
	}

	/** 待发送点数。 */
	public int size() {
		int total = 0;

		for (Segment segment : this.segments) {
			total += segment.points.size();
		}

		return total;
	}

	public boolean isThrottled() {
		return this.throttled;
	}

	/** 清空队列（切画布、放弃这一笔时用）。 */
	public void clear() {
		this.segments.clear();
		this.sentAny = false;
		this.throttled = false;
	}

	/**
	 * 每 tick 泵一次：最多 {@code maxPacketsPerTick} 个包、{@code maxPointsPerTick} 个点。
	 */
	public void tick() {
		if (this.segments.isEmpty()) {
			return;
		}

		if (this.canvasId.isEmpty()) {
			this.segments.clear();
			return;
		}

		MapDrawConfig cfg = MapDrawConfig.get();
		int maxPackets = Math.max(1, Math.min(64, cfg.maxPacketsPerTick));
		int maxPoints = Math.max(64, Math.min(65536, cfg.maxPointsPerTick));
		int perPacket = Math.max(1, Math.min(MapDrawProtocol.MAX_BATCH_POINTS, cfg.batchFlushPoints));
		int packets = 0;
		int points = 0;

		while (packets < maxPackets && points < maxPoints) {
			Segment segment = this.segments.peekFirst();

			if (segment == null) {
				break;
			}

			int take = Math.min(perPacket, Math.min(segment.points.size(), maxPoints - points));

			if (take <= 0) {
				break;
			}

			List<int[]> part = new ArrayList<>(take);

			for (int i = 0; i < take; i++) {
				part.add(segment.points.pollFirst());
			}

			boolean lastSegment = this.segments.size() == 1 && segment.points.isEmpty();

			// 新版插件：多联大板模式下用 0x12 全局坐标打点（服务端自动切片落笔）
			if (++this.diagFrames % 120 == 1) {
				top.colorgarden.mapdrawclient.MapDrawClient.LOGGER.info("[MapDrawClient] 绘制路径: {}（矩阵格数={}, 画布={}）",
						top.colorgarden.mapdrawclient.net.ServerCanvasInfo.matrixNodeCount > 0 ? "0x12 全局坐标" : "0x01/0x02 局部坐标",
						top.colorgarden.mapdrawclient.net.ServerCanvasInfo.matrixNodeCount, this.canvasId);
			}

			if (top.colorgarden.mapdrawclient.net.ServerCanvasInfo.matrixNodeCount > 0) {
				int base = top.colorgarden.mapdrawclient.net.ServerCanvasInfo.matrixBaseEntityId;

				for (int[] pt : part) {
					// 大图模式下 pt 已经是「全局大画布坐标」（见 BoardScreen.toCanvasX/Y），直接用基准展示框发 0x12
					if (base != 0) {
						MapDrawClientNetworking.drawGridPixel(base, pt[0], pt[1], segment.tool, segment.color);
					} else {
						MapDrawClientNetworking.drawPixel(this.canvasId, pt[0], pt[1], segment.tool, segment.color);
					}
				}
			} else if (part.size() == 1 && !this.sentAny && lastSegment) {
				int[] only = part.get(0);
				MapDrawClientNetworking.drawPixel(this.canvasId, only[0], only[1], segment.tool, segment.color);
			} else {
				MapDrawClientNetworking.drawBatch(this.canvasId, segment.tool, segment.color, part);
			}

			this.sentAny = true;
			packets++;
			points += take;

			if (segment.points.isEmpty()) {
				this.segments.pollFirst();
			}
		}

		if (this.throttled && this.size() < 256) {
			this.throttled = false;
		}
	}
}
