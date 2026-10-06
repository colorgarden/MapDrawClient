package top.colorgarden.mapdrawclient.net;

/**
 * 服务端回包（0x83 画布属性 / 0x84 相连矩阵）的最近一次结果。
 *
 * <p>用于界面显示：是否为 GIF 动图、多联大板尺寸等。</p>
 */
public final class ServerCanvasInfo {
	private ServerCanvasInfo() {
	}

	// ---- 0x83 画布属性 ----
	public static volatile String infoId = "";
	public static volatile String infoTitle = "";
	public static volatile int infoSize;
	public static volatile boolean animated;
	public static volatile int fps;
	public static volatile int frameCount;
	public static volatile long infoAt;

	// ---- 0x84 相连矩阵 ----
	public static volatile int matrixCols;
	public static volatile int matrixRows;
	public static volatile int matrixNodeCount;
	public static volatile int matrixTotalWidth;
	public static volatile int matrixTotalHeight;
	public static volatile long matrixAt;

	/** 相连矩阵的节点：gridCol, gridRow, entityId, canvasId, mapId。 */
	public static volatile java.util.List<Object[]> nodes = new java.util.ArrayList<>();

	/** 大板基准展示框（矩阵里第一个节点的 entityId）。 */
	public static volatile int matrixBaseEntityId;

	public static void setNodes(java.util.List<Object[]> list) {
		nodes = list;
		matrixBaseEntityId = list.isEmpty() ? 0 : (Integer) list.get(0)[2];
	}

	/**
	 * 把「某张子画布的局部坐标」换算成「大板全局坐标」。
	 *
	 * @return {baseEntityId, globalX, globalY}；无法换算返回 null。
	 */
	public static int[] globalOf(String canvasId, int localX, int localY) {
		java.util.List<Object[]> list = nodes;

		if (list == null || list.isEmpty() || canvasId == null) {
			return null;
		}

		for (Object[] node : list) {
			if (canvasId.equals(node[3])) {
				int gridCol = (Integer) node[0];
				int gridRow = (Integer) node[1];
				return new int[]{matrixBaseEntityId, gridCol * 128 + localX, gridRow * 128 + localY};
			}
		}

		return null;
	}

	public static void setInfo(String id, String title, int size, boolean isAnimated, int frameFps, int frames) {
		infoId = id == null ? "" : id;
		infoTitle = title == null ? "" : title;
		infoSize = size;
		animated = isAnimated;
		fps = frameFps;
		frameCount = frames;
		infoAt = System.currentTimeMillis();
	}

	public static void setMatrix(int cols, int rows, int nodeCount, int totalW, int totalH) {
		matrixCols = cols;
		matrixRows = rows;
		matrixNodeCount = nodeCount;
		matrixTotalWidth = totalW;
		matrixTotalHeight = totalH;
		matrixAt = System.currentTimeMillis();
	}

	/** 一行可读的描述（给状态栏用）。 */
	public static String describe(String canvasId) {
		StringBuilder sb = new StringBuilder();

		if (canvasId != null && canvasId.equals(infoId) && System.currentTimeMillis() - infoAt < 15000L) {
			sb.append(animated ? ("动图 " + frameCount + " 帧/" + fps + "fps") : "静态图");

			if (infoSize > 0) {
				sb.append(" · ").append(infoSize).append("x").append(infoSize);
			}
		}

		if (System.currentTimeMillis() - matrixAt < 15000L && matrixNodeCount > 0) {
			if (sb.length() > 0) {
				sb.append(" · ");
			}

			sb.append("相连 ").append(matrixCols).append("x").append(matrixRows)
					.append(" (").append(matrixNodeCount).append(" 格, ")
					.append(matrixTotalWidth).append("x").append(matrixTotalHeight).append("px)");
		}

		return sb.toString();
	}
}