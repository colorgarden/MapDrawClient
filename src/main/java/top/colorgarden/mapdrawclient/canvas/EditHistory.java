package top.colorgarden.mapdrawclient.canvas;

import java.util.ArrayDeque;
import java.util.Deque;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;

/**
 * 客户端本地撤销 / 重做历史。
 *
 * <p><b>为什么不用插件自己的 0x03 / 0x04：</b>插件 {@code CanvasData.pushUndoState()}
 * 每落一个像素就复制一整张 16384 像素的快照，而且栈满 30 步时丢的是
 * {@code removeLast()}（最新的那份），撤销行为完全不可靠。</p>
 *
 * <p>客户端改成「按一次操作记录像素差异」：一次笔画 / 一次油漆桶 = 一步。
 * 撤销 = 把 before 的颜色用 0x01/0x02 写回服务端，重做 = 把 after 写回去，
 * 完全不碰插件的撤销栈。每一步只存变化的像素下标与前后颜色，很省内存。</p>
 */
public final class EditHistory {
	/** 最多保留多少步。 */
	public static final int MAX_STEPS = 64;

	/** 一步操作：变化的像素下标 + 变化前/后的颜色。 */
	public record Step(int[] indices, byte[] before, byte[] after) {
		public int size() {
			return this.indices.length;
		}
	}

	private final Deque<Step> undoStack = new ArrayDeque<>();
	private final Deque<Step> redoStack = new ArrayDeque<>();
	private byte[] pending;

	/** 操作开始：记下操作前的整张画布（16KB）。 */
	public void begin(byte[] pixels) {
		this.pending = pixels == null ? null : pixels.clone();
	}

	public boolean hasPending() {
		return this.pending != null;
	}

	/** 放弃当前操作（例如中途退出界面）。 */
	public void cancel() {
		this.pending = null;
	}

	/**
	 * 操作结束：用 pending 快照与当前像素做差异，产生一步历史。
	 *
	 * @return 是否真的发生了变化（没有变化就不占一步）
	 */
	public boolean commit(byte[] pixels) {
		byte[] before = this.pending;
		this.pending = null;

		if (before == null || pixels == null || before.length != pixels.length) {
			return false;
		}

		int changed = 0;

		for (int i = 0; i < before.length; i++) {
			if (before[i] != pixels[i]) {
				changed++;
			}
		}

		if (changed == 0) {
			return false;
		}

		int[] indices = new int[changed];
		byte[] oldValues = new byte[changed];
		byte[] newValues = new byte[changed];
		int k = 0;

		for (int i = 0; i < before.length; i++) {
			if (before[i] != pixels[i]) {
				indices[k] = i;
				oldValues[k] = before[i];
				newValues[k] = pixels[i];
				k++;
			}
		}

		this.undoStack.push(new Step(indices, oldValues, newValues));

		// push 是插到队头，所以丢最旧的要用 removeLast
		while (this.undoStack.size() > MAX_STEPS) {
			this.undoStack.removeLast();
		}

		this.redoStack.clear();
		return true;
	}

	/** 撤销一步；返回的 Step 里 {@code before} 就是要写回服务端的颜色。 */
	public Step undo() {
		if (this.undoStack.isEmpty()) {
			return null;
		}

		Step step = this.undoStack.pop();
		this.redoStack.push(step);
		return step;
	}

	/** 重做一步；返回的 Step 里 {@code after} 是要写回服务端的颜色。 */
	public Step redo() {
		if (this.redoStack.isEmpty()) {
			return null;
		}

		Step step = this.redoStack.pop();
		this.undoStack.push(step);
		return step;
	}

	public int undoDepth() {
		return this.undoStack.size();
	}

	public int redoDepth() {
		return this.redoStack.size();
	}

	public void clear() {
		this.undoStack.clear();
		this.redoStack.clear();
		this.pending = null;
	}

	// ------------------------------------------------------------------
	// 把一步写回服务端
	// ------------------------------------------------------------------

	/**
	 * 把一步的变化发回服务端，并同步更新本地像素。
	 *
	 * <p>按「逻辑格」聚合并按颜色分组打包：服务端一格 = gridN x gridN 个地图像素，
	 * 一个格只发一个点就够，所以一次撤销通常只有几百个点、1~2 个包。
	 * 工具用 {@code PEN}：颜色字节 0 就是擦成透明，服务端会原样写入。</p>
	 *
	 * @param canvas   目标画布（本地也会跟着改）
	 * @param step     历史步骤
	 * @param useAfter true = 用 after（重做），false = 用 before（撤销）
	 * @return 实际发出的点数
	 */
	public static int applyStep(CanvasData canvas, Step step, boolean useAfter) {
		if (canvas == null || step == null) {
			return 0;
		}

		byte[] values = useAfter ? step.after() : step.before();
		int gridN = canvas.gridN();
		java.util.Map<Long, Byte> cells = new java.util.LinkedHashMap<>();

		for (int i = 0; i < step.indices().length; i++) {
			int pos = step.indices()[i];
			int x = pos % MapDrawProtocol.CANVAS_W;
			int y = pos / MapDrawProtocol.CANVAS_W;
			byte value = values[i];

			// 本地立即生效，保证手感
			canvas.setPixel(x, y, value);

			int cellX = Math.floorDiv(x, gridN);
			int cellY = Math.floorDiv(y, gridN);
			cells.put(((long) cellX << 20) | cellY, value);
		}

		canvas.markLocalEdit();

		// 按颜色分组
		java.util.Map<Byte, java.util.List<int[]>> byColor = new java.util.LinkedHashMap<>();

		for (java.util.Map.Entry<Long, Byte> e : cells.entrySet()) {
			long key = e.getKey();
			int cellX = (int) (key >> 20);
			int cellY = (int) (key & 0xFFFFFL);
			byColor.computeIfAbsent(e.getValue(), v -> new java.util.ArrayList<>())
					.add(new int[]{cellX * gridN, cellY * gridN});
		}

		int sent = 0;
		int chunk = Math.max(1, Math.min(1024, MapDrawConfig.get().batchFlushPoints * 16));

		for (java.util.Map.Entry<Byte, java.util.List<int[]>> e : byColor.entrySet()) {
			java.util.List<int[]> points = e.getValue();

			for (int i = 0; i < points.size(); i += chunk) {
				int end = Math.min(i + chunk, points.size());
				MapDrawClientNetworking.drawBatch(canvas.id(), MapDrawProtocol.ToolType.PEN, e.getKey(),
						new java.util.ArrayList<>(points.subList(i, end)));
				sent += end - i;
			}
		}

		return sent;
	}
}
