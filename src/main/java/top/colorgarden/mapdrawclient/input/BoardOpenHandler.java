package top.colorgarden.mapdrawclient.input;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
//#if MC < 12102
//$$ import net.minecraft.world.InteractionResultHolder;
//#endif
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.canvas.HeldMapProbe;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.ui.BoardScreen;

/**
 * 鼠标右键手势：手持画布地图直接打开画板。
 *
 * <p>为什么要拦三条路径：插件的 {@code CanvasItemInteractListener} 监听的是
 * {@code PlayerInteractEvent} 的 <b>RIGHT_CLICK_AIR 或 RIGHT_CLICK_BLOCK</b>，
 * 而且<b>不判断潜行</b>——只要手上拿着画布地图右键，服务端就会弹它自己的菜单。
 * 所以客户端必须把三条右键路径都拦下来（返回 SUCCESS，让原版不发包）：</p>
 *
 * <ul>
 *   <li>{@link UseItemCallback} —— 右键空气（原版走 useItem）</li>
 *   <li>{@link UseBlockCallback} —— 右键方块（原版走 useItemOn）</li>
 *   <li>{@link UseEntityCallback} —— 右键展示框且<b>空手蹲下</b>时，打开框里那张画布</li>
 * </ul>
 *
 * <p>插件自己的菜单没有丢：控制台菜单里有「服务端菜单 (0x0B)」按钮，
 * 走的是插件的 plugin message 通道，不依赖右键。</p>
 */
public final class BoardOpenHandler {
	private BoardOpenHandler() {
	}

	public static void init() {
		// 1) 右键空气 / 右键方块：手持画布地图 → 开我们的画板（同时阻止原版发包，插件就不会弹菜单）
		//#if MC >= 12102
		UseItemCallback.EVENT.register((player, level, hand) -> interact(player, hand));
		//#else
		//$$ UseItemCallback.EVENT.register((player, level, hand) -> {
		//$$ 	InteractionResult result = interact(player, hand);
		//$$ 	ItemStack stack = player.getItemInHand(hand);
		//$$ 	return result == InteractionResult.SUCCESS
		//$$ 			? InteractionResultHolder.success(stack)
		//$$ 			: InteractionResultHolder.pass(stack);
		//$$ });
		//#endif

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> interact(player, hand));

		// 2) 右键展示框：把手插件的「潜行右键/手持地图右键弹菜单」全部抢过来
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!level.isClientSide() || !(entity instanceof ItemFrame frame)) {
				return InteractionResult.PASS;
			}

			ItemStack inHand = player.getItemInHand(hand);

			// 拿着插件工具 = 插件自己的射线绘制手势，一律放行
			if (HeldMapProbe.isPluginTool(inHand)) {
				return InteractionResult.PASS;
			}

			// A. 手上拿着画布地图 → 打开菜单（不管潜不潜行）
			HeldMapProbe.ProbeResult heldInHand = HeldMapProbe.fromStack(inHand);

			if (heldInHand != null) {
				openMenu(heldInHand);
				return InteractionResult.SUCCESS;
			}

			// B. 展示框里是画布地图 → 也打开菜单
			//    （插件对「框里有画布」的情况同样会弹它自己的菜单，这里必须拦掉）
			if (!MapDrawConfig.get().openBoardOnFrameClick) {
				return InteractionResult.PASS;
			}

			HeldMapProbe.ProbeResult inFrame = HeldMapProbe.fromStack(frame.getItem());

			if (inFrame == null) {
				return InteractionResult.PASS;
			}

			openMenu(inFrame);
			return InteractionResult.SUCCESS;
		});
	}

	/** 三条右键路径共用的判定。 */
	private static InteractionResult interact(net.minecraft.world.entity.player.Player player, InteractionHand hand) {
		if (!MapDrawConfig.get().openBoardOnRightClick || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		HeldMapProbe.ProbeResult held = HeldMapProbe.fromStack(player.getItemInHand(hand));

		if (held == null) {
			return InteractionResult.PASS;
		}

		// 右键画布地图 = 打开客户端二级菜单（不是直接开画板，也不是插件那个原生菜单）
		openMenu(held);

		// SUCCESS = 不让原版继续处理（也就不会把这次右键发给服务端，插件菜单不会弹）
		return InteractionResult.SUCCESS;
	}

	/**
	 * 供控制台菜单「打开画板」按钮调用。
	 *
	 * <p>注意：这里<b>不能</b>判断「当前是否已有界面」——从菜单点进来时菜单本身就是当前界面，
	 * 之前那样判断会导致按钮点了没反应。</p>
	 */
	public static void openBoardFromHeldMap() {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return;
		}

		HeldMapProbe.ProbeResult held = HeldMapProbe.scanPlayerInventory();
		String id = held != null ? held.canvasId() : CanvasStore.INSTANCE.currentId();

		if (held != null) {
			CanvasStore.INSTANCE.setCurrent(id);
			CanvasStore.INSTANCE.setStatus("已打开画布: "
					+ (held.title().isEmpty() ? held.canvasId() : held.title()), 0xFF55FF55);
		} else {
			CanvasStore.INSTANCE.setStatus(id.isEmpty()
					? "没找到画布地图，可在画板里点「新建」或「列表」"
					: "已打开当前画布", id.isEmpty() ? 0xFFFFD24A : 0xFF55FF55);
		}

		top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new BoardScreen(id));
	}

	/** 右键画布地图 → 打开**客户端控制台菜单**（二级菜单），从那里再进画板。 */
	private static void openMenu(HeldMapProbe.ProbeResult held) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return;
		}

		CanvasStore.INSTANCE.setCurrent(held.canvasId());
		CanvasStore.INSTANCE.setStatus("已选中画布: "
				+ (held.title().isEmpty() ? held.canvasId() : held.title()), 0xFF55FF55);
		MapDrawClientNetworking.requestCanvas(held.canvasId());
		top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new top.colorgarden.mapdrawclient.ui.MainMenuScreen(null));
	}

	/** 直接打开画板（菜单里的「打开画板」用）。 */
	private static void openBoard(HeldMapProbe.ProbeResult held) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return;
		}

		CanvasStore.INSTANCE.setCurrent(held.canvasId());
		CanvasStore.INSTANCE.setStatus("已打开画布: "
				+ (held.title().isEmpty() ? held.canvasId() : held.title()), 0xFF55FF55);
		top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new BoardScreen(held.canvasId()));
	}

	/** 手持物品是否是画布地图（给别处判断用）。 */
	public static boolean isHoldingCanvasMap() {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return false;
		}

		ItemStack stack = client.player.getMainHandItem();
		return HeldMapProbe.fromStack(stack) != null;
	}
}
