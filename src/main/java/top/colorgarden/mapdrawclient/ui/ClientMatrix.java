package top.colorgarden.mapdrawclient.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.AABB;

import top.colorgarden.mapdrawclient.MapDrawClient;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.canvas.HeldMapProbe;
import top.colorgarden.mapdrawclient.net.ServerCanvasInfo;

/**
 * 客户端自己识别「多联大图」矩阵（不依赖服务端 0x11）。
 *
 * <p>地图标题里带拼接信息：{@code 画作 #8962 [2,3]} → 组号 8962、第 2 列、第 3 行。
 * 扫描附近展示框把同组地图收集起来即可拼出矩阵，行列总数取组内最大行列。</p>
 */
public final class ClientMatrix {
	private static final Pattern TITLE = Pattern.compile("#(\\d+)\\s*\\[(\\d+)\\s*,\\s*(\\d+)\\]");

	private ClientMatrix() {
	}

	/** 以某个展示框为基准，扫描附近同组地图并写入 {@link ServerCanvasInfo}。 */
	public static void detect(Minecraft client, ItemFrame startFrame) {
		try {
			if (client.player == null || client.level == null || startFrame == null) {
				return;
			}

			HeldMapProbe.ProbeResult start = HeldMapProbe.fromStack(startFrame.getItem());

			if (start == null || start.title() == null) {
				return;
			}

			Matcher sm = TITLE.matcher(start.title());

			if (!sm.find()) {
				return; // 不是拼接画作
			}

			String group = sm.group(1);
			AABB box = client.player.getBoundingBox().inflate(8.0);
			List<ItemFrame> frames = client.level.getEntitiesOfClass(ItemFrame.class, box);
			List<Object[]> nodes = new ArrayList<>();
			int maxCol = 0;
			int maxRow = 0;
			int baseEntityId = 0;
			int bestCol = Integer.MAX_VALUE;
			int bestRow = Integer.MAX_VALUE;

			for (ItemFrame frame : frames) {
				HeldMapProbe.ProbeResult probe = HeldMapProbe.fromStack(frame.getItem());

				if (probe == null || probe.title() == null) {
					continue;
				}

				Matcher m = TITLE.matcher(probe.title());

				if (!m.find() || !group.equals(m.group(1))) {
					continue;
				}

				int col = Integer.parseInt(m.group(2));
				int row = Integer.parseInt(m.group(3));
				maxCol = Math.max(maxCol, col);
				maxRow = Math.max(maxRow, row);
				nodes.add(new Object[]{col - 1, row - 1, frame.getId(), probe.canvasId(), 0});

				if (col < bestCol || (col == bestCol && row < bestRow)) {
					bestCol = col;
					bestRow = row;
					baseEntityId = frame.getId();
				}
			}

			if (nodes.size() <= 1) {
				return;
			}

			ServerCanvasInfo.setNodes(nodes);
			ServerCanvasInfo.setMatrix(Math.max(maxCol, 1), Math.max(maxRow, 1), nodes.size(), 0, 0);
			ServerCanvasInfo.matrixBaseEntityId = baseEntityId;
			MapDrawClient.LOGGER.info("[MapDrawClient] 客户端识别到拼接矩阵: 组={} {}x{} 共 {} 格（不依赖服务端 0x11）",
					group, maxCol, maxRow, nodes.size());

			for (Object[] node : nodes) {
				String id = (String) node[3];

				if (CanvasStore.INSTANCE.get(id) == null) {
					top.colorgarden.mapdrawclient.net.MapDrawClientNetworking.requestCanvas(id);
				}
			}
		} catch (Throwable t) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] 客户端矩阵识别失败: {}", t.toString());
		}
	}
}