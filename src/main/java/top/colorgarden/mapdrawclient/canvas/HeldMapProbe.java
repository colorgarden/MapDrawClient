package top.colorgarden.mapdrawclient.canvas;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import top.colorgarden.mapdrawclient.MapDrawClient;

/**
 * 从「手持的地图画布」里直接读出画布 ID。
 *
 * <p>原理：MapDraw 插件用 Bukkit 的 {@code PersistentDataContainer} 在画布地图上写了
 * {@code mapdraw:canvas_id} 等键；Paper 会把 PDC 序列化进物品的 {@code minecraft:custom_data}
 * 组件，结构为 {@code {PublicBukkitValues: {"mapdraw:canvas_id": "...", ...}}}。
 * 于是客户端不需要任何额外通道就能拿到画布 ID。</p>
 *
 * <p>键名与插件 {@code CanvasNBTUtil} 中的 {@code NamespacedKey(plugin, "...")} 一一对应，
 * 已用 javap 反编译插件确认。</p>
 */
public final class HeldMapProbe {
	private HeldMapProbe() {
	}

	/** Paper 写入的 PDC 根 compound。 */
	public static final String PDC_ROOT = "PublicBukkitValues";

	public static final String KEY_IS_CANVAS = "mapdraw:is_canvas";
	public static final String KEY_CANVAS_ID = "mapdraw:canvas_id";
	public static final String KEY_TITLE = "mapdraw:title";
	public static final String KEY_DESCRIPTION = "mapdraw:description";
	public static final String KEY_SIZE = "mapdraw:size";
	public static final String KEY_PROTECTED = "mapdraw:protected";
	public static final String KEY_NO_COPY = "mapdraw:no_copy";
	public static final String KEY_CREATOR = "mapdraw:creator";

	/** 插件给自己的画笔/橡皮/油漆桶写的是这个键（`ToolManager` 里 new NamespacedKey(plugin, "tool_type")）。 */
	public static final String KEY_TOOL_TYPE = "mapdraw:tool_type";

	/** 探测结果。 */
	public record ProbeResult(String canvasId, String title, String creator, boolean canvasFlag,
			boolean noCopyFlag, boolean protectedFlag) {
	}

	/** 读主手物品；不是画布地图返回 null。 */
	public static ProbeResult fromMainHand() {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null) {
			return null;
		}

		return fromStack(player.getMainHandItem());
	}

	/**
	 * 扫描主手、副手与整个背包，找出第一张画布地图。
	 * 插件创建画布后是 {@code player.getInventory().addItem(...)}，所以新画布一定在背包里。
	 */
	public static ProbeResult scanPlayerInventory() {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null) {
			return null;
		}

		ProbeResult result = fromStack(player.getMainHandItem());

		if (result != null) {
			return result;
		}

		result = fromStack(player.getOffhandItem());

		if (result != null) {
			return result;
		}

		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			result = fromStack(inventory.getItem(slot));

			if (result != null) {
				return result;
			}
		}

		return null;
	}

	/**
	 * 扫描主手、副手与整个背包，找出「不在 {@code known} 里」的第一张画布地图。
	 *
	 * <p>新建画布时必须用这个：玩家背包里往往已经躺着好几张旧画布地图，
	 * {@link #scanPlayerInventory()} 只会返回最先找到的那张（通常是旧的），
	 * 拿它去清空会清错人——旧画作被抹掉，而刚建的新画布还是满屏白底。</p>
	 *
	 * @param known 已经见过的画布 ID（一般是客户端缓存里的所有 ID）
	 * @return 新出现的画布；没有新画布返回 null
	 */
	public static ProbeResult scanForNewCanvas(java.util.Set<String> known) {
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null) {
			return null;
		}

		ProbeResult held = fromStack(player.getMainHandItem());

		if (held != null && (known == null || !known.contains(held.canvasId()))) {
			return held;
		}

		ProbeResult off = fromStack(player.getOffhandItem());

		if (off != null && (known == null || !known.contains(off.canvasId()))) {
			return off;
		}

		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ProbeResult result = fromStack(inventory.getItem(slot));

			if (result != null && (known == null || !known.contains(result.canvasId()))) {
				return result;
			}
		}

		return null;
	}

	/** 背包里所有画布地图（主手/副手/背包，按槽位顺序，去重）。 */
	public static java.util.List<ProbeResult> scanAll() {
		java.util.List<ProbeResult> out = new java.util.ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;

		if (player == null) {
			return out;
		}

		addProbe(out, seen, player.getMainHandItem());
		addProbe(out, seen, player.getOffhandItem());
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			addProbe(out, seen, inventory.getItem(slot));
		}

		return out;
	}

	private static void addProbe(java.util.List<ProbeResult> out, java.util.Set<String> seen, ItemStack stack) {
		ProbeResult result = fromStack(stack);

		if (result != null && seen.add(result.canvasId())) {
			out.add(result);
		}
	}

	/** 诊断用：把物品 PDC 里的键名列出来（没有就返回 "-"）。 */
	public static String pdcKeys(ItemStack stack) {
		try {
			CompoundTag pdc = pdcOf(stack);

			if (pdc == null) {
				return "-";
			}

			return pdc.toString();
		} catch (Throwable t) {
			return "<读取失败:" + t.getClass().getSimpleName() + ">";
		}
	}

	/** 读物品上的地图 ID（{@code minecraft:map_id} 组件）；没有返回 -1。 */
	public static int mapIdOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return -1;
		}

		try {
			net.minecraft.world.level.saveddata.maps.MapId id = stack.get(DataComponents.MAP_ID);
			return id == null ? -1 : id.id();
		} catch (Throwable t) {
			return -1;
		}
	}

	/** 只知道画布 ID 时构造一个探测结果（展示框里的地图按 mapId 反查时用）。 */
	public static ProbeResult fromMapId(String canvasId) {
		if (canvasId == null || canvasId.isEmpty()) {
			return null;
		}

		return new ProbeResult(canvasId, "", "", true, false, false);
	}

	/** 取出 {@code PublicBukkitValues} 这一层；没有就返回 null。 */
	public static CompoundTag pdcOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);

		if (data == null || data.isEmpty()) {
			return null;
		}

		return top.colorgarden.mapdrawclient.compat.Compat.nbtCompound(data.copyTag(), PDC_ROOT);
	}

	/**
	 * 该物品是不是插件发的绘图工具（画笔 / 橡皮擦 / 油漆桶）。
	 *
	 * <p>工具上写的是 {@code mapdraw:tool_type}，画布地图上写的是 {@code mapdraw:canvas_id}。
	 * 靠这个区分「玩家的工具绘制手势」和「手持画布地图」——前者必须放行给插件自己的射线绘制。</p>
	 */
	public static boolean isPluginTool(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}

		try {
			CompoundTag pdc = pdcOf(stack);
			return pdc != null && pdc.contains(KEY_TOOL_TYPE);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 读指定物品；不是画布地图返回 null。 */
	public static ProbeResult fromStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}

		try {
			CompoundTag pdc = pdcOf(stack);

			if (pdc == null) {
				return null;
			}

			String idValue = top.colorgarden.mapdrawclient.compat.Compat.nbtString(pdc, KEY_CANVAS_ID);

			if (idValue == null || idValue.isEmpty()) {
				return null;
			}

			return new ProbeResult(
					idValue,
					top.colorgarden.mapdrawclient.compat.Compat.nbtString(pdc, KEY_TITLE) == null ? "" : top.colorgarden.mapdrawclient.compat.Compat.nbtString(pdc, KEY_TITLE),
					top.colorgarden.mapdrawclient.compat.Compat.nbtString(pdc, KEY_CREATOR) == null ? "" : top.colorgarden.mapdrawclient.compat.Compat.nbtString(pdc, KEY_CREATOR),
					pdc.contains(KEY_IS_CANVAS),
					pdc.contains(KEY_NO_COPY),
					pdc.contains(KEY_PROTECTED));
		} catch (Throwable t) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] 读取手持地图 PDC 失败: {}", t.toString());
			return null;
		}
	}
}
