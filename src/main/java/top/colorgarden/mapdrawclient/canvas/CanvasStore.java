package top.colorgarden.mapdrawclient.canvas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawClient;
import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;
import top.colorgarden.mapdrawclient.net.MapDrawProtocol;
import top.colorgarden.mapdrawclient.ui.BoardScreen;

/**
 * 客户端画布仓库：缓存服务端同步下来的画布、维护当前选中项、
 * 处理 0x80 回执与 0x81 同步，并在需要时延迟重同步。
 */
public final class CanvasStore {
	public static final CanvasStore INSTANCE = new CanvasStore();

	private final Map<String, CanvasData> byId = new LinkedHashMap<>();
	private final Map<Integer, String> idByMapId = new HashMap<>();
	private final Map<String, EditHistory> histories = new HashMap<>();
	/** 空历史占位（画布 ID 为空时用）。 */
	private static final EditHistory EMPTY_HISTORY = new EditHistory();
	private final Set<String> autoOpened = new HashSet<>();
	private final Set<String> stale = new LinkedHashSet<>();

	private String currentId = "";
	private String status = "就绪";
	private int statusColor = 0xFFB0B0B0;
	private long statusAt = System.currentTimeMillis();
	private int tickCounter;
	private int joinProbeTicks = -1;
	private int selfTestTicks = -1;
	private int openMenuTicks = -1;
	private int openMenuAttempts;
	private boolean expectPluginMenu;
	private int expectPluginMenuTicks;
	/**
	 * 玩家已经进入插件的 GUI（主动点「服务端菜单」0x0B）。
	 *
	 * <p>插件自己的菜单是箱子界面，点条目还会开子菜单。如果只放行第一个实例，
	 * 子菜单会被当成「插件乱弹的菜单」关掉 —— 服务端却以为界面还开着，继续发
	 * {@code container_set_slot}，客户端此时已经没有那个容器了，就会
	 * {@code IndexOutOfBoundsException: Index 82 out of bounds for length 46}
	 * 直接断线。所以进入插件 GUI 后要一直放行，直到玩家真的退出。</p>
	 */
	private boolean pluginGuiMode;
	private int pluginGuiGraceTicks;
	/** 「刚打开过客户端界面」的保护时间窗，避免同步把界面顶掉（右键菜单闪画板就是这个原因）。 */
	private int screenGuardTicks;
	private boolean clearAfterCreate;
	private int clearAfterCreateTicks;
	private int clearAfterCreateAttempts;
	private final Set<String> knownIdsBeforeCreate = new HashSet<>();
	/** 本次连接里同步过的所有画布 ID（用来判断「哪张是新出现的」）。 */
	private final Set<String> seenCanvasIds = new HashSet<>();
	private int selfTestStage;
	private String selfTestCanvasId = "";
	private boolean initialised;

	private CanvasStore() {
	}

	public void init() {
		if (this.initialised) {
			return;
		}

		this.initialised = true;
		ClientTickEvents.END_CLIENT_TICK.register(client -> this.tick(client));

		// 插件菜单是服务端 openInventory 弹出来的真实容器界面，只靠 tick 兜底会闪 1~2 帧；
		// 挂在 Fabric 的 ScreenEvents.AFTER_INIT 上，可以在它第一次渲染之前就换成客户端界面。
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> this.interceptScreen(client, screen));

		// 进入服务器：提示当前状态 + 稍后检查通道是否真的可用
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
			this.setStatus("已连接：按 J 打开控制台菜单（手持画布地图右键也行）", 0xFFB0B0B0);
			this.joinProbeTicks = 60;
			this.selfTestStage = 0;
			this.selfTestTicks = MapDrawConfig.get().selfTest ? 80 : -1;
			this.openMenuTicks = MapDrawConfig.get().openMenuOnJoin ? 60 : -1;
			this.openMenuAttempts = 0;
		});

		// 断开/换服：清空缓存，避免把上一个服务器的画布带到新连接里
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> this.reset());
	}

	/** 标记「接下来会出现我们自己要求打开的原生菜单」，兜底拦截放行它。 */
	public void expectPluginMenu() {
		this.expectPluginMenu = true;
		this.expectPluginMenuTicks = 120;
	}

	/**
	 * 记录「刚刚有客户端界面被打开」。
	 *
	 * <p>右键画布地图 → 控制台菜单 → 这条链路里服务端还会推一次 0x81，
	 * 如果这时屏幕刚好是空的，{@code autoOpenBoardOnSync} 就会把画板顶上来，
	 * 表现为「菜单闪一下、画板闪一下、又回到菜单」。有了这个时间窗就不会再乱开界面。</p>
	 */
	public void noteClientScreenOpened() {
		this.screenGuardTicks = 40;
		// 打开我们的界面 = 已经离开插件的箱子 GUI
		this.pluginGuiMode = false;
		this.pluginGuiGraceTicks = 0;
	}

	/**
	 * 把刚新建的画布整张清成透明（插件默认会用 {@code canvas.default_bg_color} 把整张
	 * 128x128 填成纯白，所以刚建好的画布在游戏里看就是「一张全满的白图」）。
	 *
	 * <p>清空只用「每个逻辑格一个点」：{@code size=16} 时一个逻辑像素等于
	 * {@code 8x8} 个地图像素，插件收到任意一格内的坐标都会把整格刷成同色，
	 * 所以 16x16 画布只需 256 个点（1 个包），128x128 画布才需要 16384 个点。
	 * 工具用 {@code ERASER}：插件 {@code DrawingEngine} 里橡皮擦分支恒定写 0，
	 * 不受画笔颜色字节约定的影响，语义最稳。</p>
	 *
	 * @return true = 已经处理完（清空成功，或确实没有新画布）；false = 稍后重试
	 */
	private boolean clearNewestCanvas() {
		if (!MapDrawConfig.get().clearNewCanvas) {
			return true;
		}

		Set<String> known = new HashSet<>(this.knownIdsBeforeCreate);
		known.addAll(this.byId.keySet());
		known.addAll(this.seenCanvasIds);

		HeldMapProbe.ProbeResult found = HeldMapProbe.scanForNewCanvas(known);

		if (found == null) {
			return false;
		}

		String canvasId = found.canvasId();
		CanvasData cached = this.get(canvasId);
		int size = cached != null ? cached.size() : Math.max(1, MapDrawConfig.get().newCanvasSize);

		this.sendClear(canvasId, size);
		this.knownIdsBeforeCreate.add(canvasId);
		this.setStatus("新画布已清空为透明（可用 clearNewCanvas 关闭此行为）", 0xFF55FF55);
		return true;
	}

	/**
	 * 真正发清空包：每个逻辑格一个点，工具用橡皮擦（服务端恒定写 0 = 透明）。
	 *
	 * @param canvasId 目标画布
	 * @param size     画布逻辑分辨率
	 */
	private void sendClear(String canvasId, int size) {
		int gridN = MapDrawProtocol.gridFor(size);
		int cells = Math.min(Math.max(size, 1), MapDrawProtocol.CANVAS_W);
		List<int[]> points = new ArrayList<>(cells * cells);

		for (int gy = 0; gy < cells; gy++) {
			for (int gx = 0; gx < cells; gx++) {
				points.add(new int[]{gx * gridN, gy * gridN});
			}
		}

		int chunk = 1024;

		for (int i = 0; i < points.size(); i += chunk) {
			List<int[]> part = new ArrayList<>(points.subList(i, Math.min(i + chunk, points.size())));
			MapDrawClientNetworking.drawBatch(canvasId, MapDrawProtocol.ToolType.ERASER,
					MapDrawProtocol.TRANSPARENT_COLOR, part);
		}

		CanvasData cached = this.get(canvasId);

		if (cached != null) {
			java.util.Arrays.fill(cached.pixels(), MapDrawProtocol.TRANSPARENT_COLOR);
			cached.markSynced();
		}

		this.seenCanvasIds.add(canvasId);
		MapDrawClient.LOGGER.info("[MapDrawClient] 已把新画布 {} ({}x{} 逻辑格, gridN={}) 清空为透明，共 {} 个点",
				canvasId, cells, cells, gridN, points.size());
	}

	/**
	 * 兜底拦截：插件自己弹出来的原生菜单（玩家没主动要的）直接关掉，换成客户端界面。
	 *
	 * <p>插件能从很多路径弹菜单（拿着画布右键空气/方块、右键展示框、潜行、
	 * 甚至是延迟一拍调度的任务），客户端事件不一定全拦得住，所以这里再保一层：
	 * 一是挂在 Fabric 的 {@code ScreenEvents.AFTER_INIT}（第一次渲染之前就换掉，
	 * 完全不会看到服务端菜单闪一下），二是每 tick 再兜一层。</p>
	 */
	private void interceptScreen(Minecraft client, Screen screen) {
		if (!MapDrawConfig.get().suppressPluginMenu) {
			return;
		}

		if (!(screen instanceof AbstractContainerScreen<?> container)) {
			return;
		}

		String title = container.getTitle() == null ? "" : container.getTitle().getString();

		if (!title.contains("MapDraw")) {
			return;
		}

		// 玩家主动要的服务端界面（菜单里的「服务端菜单 / 服务端调色板」按钮，0x0B）：
		// 放行；并且进入「插件 GUI 模式」——插件自己的子菜单也一律放行，
		// 否则关掉服务端界面会让服务端继续给一个已关闭的容器发槽位更新 → 协议错误断线。
		if (this.expectPluginMenu || this.pluginGuiMode) {
			this.expectPluginMenu = false;
			this.expectPluginMenuTicks = 0;
			this.pluginGuiMode = true;
			this.pluginGuiGraceTicks = 0;
			MapDrawClient.LOGGER.info("[MapDrawClient] 放行玩家主动打开的服务端界面: {}", title);
			return;
		}

		MapDrawClient.LOGGER.info("[MapDrawClient] 拦截到插件原生菜单并替换为客户端界面: {}", title);
		this.setStatus("已拦截插件原生菜单，打开客户端菜单（需要服务端菜单时在菜单里点）", 0xFF55FF55);
		top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new top.colorgarden.mapdrawclient.ui.MainMenuScreen(null));
	}

	/** 每 tick 的兜底拦截（正常情况已经被 ScreenEvents 抢在前面换掉了）。 */
	private void suppressNativeMenu(Minecraft client) {
		this.interceptScreen(client, top.colorgarden.mapdrawclient.compat.Compat.screen(client));
	}

	/** 清空所有跨连接状态。 */
	public void reset() {
		this.byId.clear();
		this.idByMapId.clear();
		this.autoOpened.clear();
		this.stale.clear();
		this.seenCanvasIds.clear();
		this.knownIdsBeforeCreate.clear();
		this.histories.clear();
		this.clearAfterCreate = false;
		this.clearAfterCreateTicks = 0;
		this.clearAfterCreateAttempts = 0;
		this.currentId = "";
		this.tickCounter = 0;
		this.joinProbeTicks = -1;
		this.selfTestTicks = -1;
		this.setStatus("已断开连接，画布缓存已清空", 0xFFB0B0B0);
	}

	/**
	 * 尝试读出手持地图里的画布 ID 并设为当前画布。
	 *
	 * @return 成功识别返回 true
	 */
	public boolean useHeldMap() {
		HeldMapProbe.ProbeResult held = HeldMapProbe.fromMainHand();

		if (held == null) {
			this.setStatus("主手不是 MapDraw 画布地图 (需要手持由插件生成的地图画)", 0xFFFFD24A);
			return false;
		}

		this.setCurrent(held.canvasId());
		this.setStatus("已从手持地图识别画布: "
				+ (held.title().isEmpty() ? held.canvasId() : held.title()), 0xFF55FF55);
		MapDrawClientNetworking.requestCanvas(held.canvasId());
		return true;
	}

	/** 联调自检状态机：通道 → 创建 → PDC → 同步 → 落笔 → 回读验证。 */
	private void runSelfTestStage() {
		MapDrawClient.LOGGER.info("[SelfTest] ===== 阶段 {} =====", this.selfTestStage);

		switch (this.selfTestStage) {
			case 0 -> {
				MapDrawClient.LOGGER.info("[SelfTest] mapdraw:main 通道可用 = {}", MapDrawClientNetworking.canSend());
				HeldMapProbe.ProbeResult held = HeldMapProbe.scanPlayerInventory();

				if (held != null) {
					MapDrawClient.LOGGER.info("[SelfTest] 背包里已有画布: canvas_id={} title={}",
							held.canvasId(), held.title());
					this.selfTestCanvasId = held.canvasId();
					MapDrawClientNetworking.requestCanvas(held.canvasId());
					this.nextStage(2, 60);
				} else {
					MapDrawClient.LOGGER.info("[SelfTest] 背包里没有画布地图，发送 0x08 CREATE_CANVAS (16x16 便于核对渲染)");
					MapDrawClientNetworking.createCanvas("MapDrawClient 自检", 16);
					this.nextStage(1, 60);
				}
			}
			case 1 -> {
				HeldMapProbe.ProbeResult held = HeldMapProbe.scanPlayerInventory();

				if (held == null) {
					MapDrawClient.LOGGER.warn("[SelfTest] 仍未在背包里找到画布地图（创建可能被服务端拒绝）");
					this.nextStage(-1, 0);
					return;
				}

				MapDrawClient.LOGGER.info("[SelfTest] 创建成功，从物品 PDC 读到 canvas_id={} title={}",
						held.canvasId(), held.title());
				this.selfTestCanvasId = held.canvasId();
				MapDrawClientNetworking.requestCanvas(held.canvasId());
				this.nextStage(2, 60);
			}
			case 2 -> {
				CanvasData canvas = this.get(this.selfTestCanvasId);

				if (canvas == null) {
					MapDrawClient.LOGGER.warn("[SelfTest] 没有收到 0x81 同步，画布缓存为空");
					this.nextStage(-1, 0);
					return;
				}

				MapDrawClient.LOGGER.info("[SelfTest] 收到画布: id={} mapId={} size={} 保护={} 动图={} 已落笔像素={}",
						canvas.id(), canvas.mapId(), canvas.size(), canvas.isProtected(), canvas.animated(),
						this.countPainted(canvas));
				this.logCanvasStats("落笔前");
				MapDrawClientNetworking.drawPixel(canvas.id(), 10, 10, MapDrawProtocol.ToolType.PEN, (byte) 114);
				MapDrawClient.LOGGER.info("[SelfTest] 已发送 0x01 DRAW_PIXEL: (10,10) = 114(红)");
				this.nextStage(3, 60);
			}
			case 3 -> {
				MapDrawClient.LOGGER.info("[SelfTest] 重新拉取画布以验证落笔是否生效");
				MapDrawClientNetworking.requestCanvas(this.selfTestCanvasId);
				this.nextStage(4, 80);
			}
			case 4 -> {
				CanvasData canvas = this.get(this.selfTestCanvasId);
				int pixel = canvas == null ? -1 : (canvas.pixel(10, 10) & 0xFF);
				MapDrawClient.LOGGER.info("[SelfTest] 回读 (10,10) 像素 = {} ({})", pixel,
						pixel == 114 ? "PASS 落笔已生效，整链路 OK" : "FAIL 与服务端不一致");
				this.logCanvasStats("落笔后");
				this.nextStage(5, 40);
			}
			case 5 -> {
				MapDrawClient.LOGGER.info("[SelfTest] 验证 0x09 SET_TOOL (依次 笔/擦/桶/无)");
				MapDrawClientNetworking.setTool(MapDrawProtocol.ToolType.PEN);
				MapDrawClientNetworking.setTool(MapDrawProtocol.ToolType.ERASER);
				MapDrawClientNetworking.setTool(MapDrawProtocol.ToolType.PAINTBUCKET);
				MapDrawClientNetworking.setTool(MapDrawProtocol.ToolType.NONE);
				this.nextStage(6, 40);
			}
			case 6 -> {
				MapDrawClient.LOGGER.info("[SelfTest] 验证 0x01 油漆桶 (40,40)");
				MapDrawClientNetworking.drawPixel(this.selfTestCanvasId, 40, 40,
						MapDrawProtocol.ToolType.PAINTBUCKET, (byte) 78);
				this.nextStage(7, 40);
			}
			case 7 -> {
				MapDrawClient.LOGGER.info("[SelfTest] 验证 0x07 SET_META: 标题 / 防拷贝");
				MapDrawClientNetworking.setMeta(this.selfTestCanvasId, MapDrawProtocol.MetaField.TITLE, "自检标题");
				MapDrawClientNetworking.setMeta(this.selfTestCanvasId, MapDrawProtocol.MetaField.NO_COPY, "true");
				this.nextStage(8, 40);
			}
			case 8 -> {
				MapDrawClient.LOGGER.info("[SelfTest] 逐端口验证结束，重新同步看结果");
				MapDrawClientNetworking.requestCanvas(this.selfTestCanvasId);
				this.nextStage(9, 60);
			}
			case 9 -> {
				CanvasData canvas = this.get(this.selfTestCanvasId);

				if (canvas != null) {
					MapDrawClient.LOGGER.info("[SelfTest] 最终状态: 标题={} 防拷贝={} 保护={} (40,40)像素={}",
							canvas.title(), canvas.noCopy(), canvas.isProtected(), canvas.pixel(40, 40) & 0xFF);
				}

				this.logCanvasStats("全部操作之后");

				this.nextStage(-1, 0);

				// 自检收尾：打开控制台菜单，方便截图核对新界面
				Minecraft client = Minecraft.getInstance();

				if (top.colorgarden.mapdrawclient.compat.Compat.screen(client) == null) {
					top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new top.colorgarden.mapdrawclient.ui.MainMenuScreen(null));
					MapDrawClient.LOGGER.info("[SelfTest] 已自动打开控制台菜单（键位 J 同效）");
				}

				// 自检只跑一次：跑完自动关掉，免得每次进服都建画布
				MapDrawConfig cfg = MapDrawConfig.get();

				if (cfg.selfTest) {
					cfg.selfTest = false;
					MapDrawConfig.save();
					MapDrawClient.LOGGER.info("[SelfTest] 已自动关闭 selfTest 并保存配置");
				}
			}
			default -> this.nextStage(-1, 0);
		}
	}

	private void nextStage(int stage, int delayTicks) {
		this.selfTestStage = stage;
		this.selfTestTicks = stage < 0 ? -1 : delayTicks;
	}

	private int countPainted(CanvasData canvas) {
		int painted = 0;

		for (byte b : canvas.pixels()) {
			if (b != 0) {
				painted++;
			}
		}

		return painted;
	}

	/** 自检用：统计「可用区内」与「可用区外」被填充的像素数，用来判断是谁把画布填满的。 */
	private void logCanvasStats(String tag) {
		CanvasData canvas = this.get(this.selfTestCanvasId);

		if (canvas == null) {
			MapDrawClient.LOGGER.info("[SelfTest] {}: 无画布数据", tag);
			return;
		}

		int size = canvas.size();
		int inside = 0;
		int outside = 0;
		int firstOutside = -1;

		for (int y = 0; y < MapDrawProtocol.CANVAS_H; y++) {
			for (int x = 0; x < MapDrawProtocol.CANVAS_W; x++) {
				if (canvas.pixel(x, y) == 0) {
					continue;
				}

				if (x < size && y < size) {
					inside++;
				} else {
					outside++;

					if (firstOutside < 0) {
						firstOutside = y * MapDrawProtocol.CANVAS_W + x;
					}
				}
			}
		}

		MapDrawClient.LOGGER.info("[SelfTest] {}: size={} 可用区内已落笔={} 可用区外被填={} 首个区外像素索引={}",
				tag, size, inside, outside, firstOutside);
	}

	// ------------------------------------------------------------------
	// 事件
	// ------------------------------------------------------------------

	private void tick(Minecraft client) {
		this.tickCounter++;

		// 落笔发包限速泵：每 tick 只按配额发一点，避免被 Paper 的 packet-limiter 踢掉。
		// 放在这里（而不是画板里）是为了画板关掉后也能把积压的点发完。
		MapDrawClientNetworking.pumpDrawQueue();

		if (this.joinProbeTicks > 0 && --this.joinProbeTicks == 0) {
			boolean canSend = MapDrawClientNetworking.canSend();
			MapDrawClient.LOGGER.info("[MapDrawClient] 连接检查: mapdraw:main 通道可用 = {}", canSend);

			if (!canSend) {
				MapDrawClient.LOGGER.warn("[MapDrawClient] 服务端未声明 mapdraw:main，请确认 MapDraw 插件已启用");
				this.setStatus("服务端未声明 mapdraw:main 通道", 0xFFFF6666);
			} else {
				this.setStatus("mapdraw:main 通道就绪：手持画布地图右键开画板", 0xFF55FF55);
			}
		}

		if (this.selfTestTicks > 0 && --this.selfTestTicks == 0) {
			this.runSelfTestStage();
		}

		if (this.openMenuTicks > 0 && --this.openMenuTicks == 0) {
			// 进服后前几秒可能还挂着「加载地形中」界面，失败就过 20 tick 再试
			if (top.colorgarden.mapdrawclient.compat.Compat.screen(client) == null && client.player != null) {
				top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new top.colorgarden.mapdrawclient.ui.MainMenuScreen(null));
				MapDrawClient.LOGGER.info("[MapDrawClient] openMenuOnJoin: 已自动打开控制台菜单");
				this.openMenuTicks = -1;
			} else if (this.openMenuAttempts++ < 10) {
				this.openMenuTicks = 20;
			} else {
				MapDrawClient.LOGGER.warn("[MapDrawClient] openMenuOnJoin: 多次尝试后仍无法打开菜单");
				this.openMenuTicks = -1;
			}
		}

		if (this.expectPluginMenuTicks > 0 && --this.expectPluginMenuTicks == 0) {
			this.expectPluginMenu = false;
		}

		if (this.screenGuardTicks > 0) {
			this.screenGuardTicks--;
		}

		// 放行中的服务端界面已经关掉了 → 清掉记录，下次插件自己弹菜单照样拦
		if (this.pluginGuiMode) {
			Screen current = top.colorgarden.mapdrawclient.compat.Compat.screen(client);
			boolean inPluginGui = current instanceof AbstractContainerScreen<?> container
					&& container.getTitle() != null
					&& container.getTitle().getString().contains("MapDraw");

			if (inPluginGui) {
				this.pluginGuiGraceTicks = 0;
			} else if (current == null) {
				// 关掉界面时会短暂出现 null（子菜单切换也可能），给 10 tick 宽限
				if (++this.pluginGuiGraceTicks > 10) {
					this.pluginGuiMode = false;
					this.pluginGuiGraceTicks = 0;
				}
			} else {
				// 玩家打开了别的界面（自己的背包、别的箱子、或我们的界面）→ 退出插件 GUI 模式
				this.pluginGuiMode = false;
				this.pluginGuiGraceTicks = 0;
			}
		}

		if (this.clearAfterCreate && --this.clearAfterCreateTicks <= 0) {
			// 新画布地图可能比回执晚几拍才进背包，所以失败要重试，别静默放弃
			if (this.clearNewestCanvas()) {
				this.clearAfterCreate = false;
			} else if (this.clearAfterCreateAttempts++ >= 12) {
				this.clearAfterCreate = false;
				this.setStatus("新建后没在背包里找到新画布地图，未执行清空（可在画板上按 H 读手持地图）", 0xFFFFD24A);
				MapDrawClient.LOGGER.warn("[MapDrawClient] 新建画布后 12 次重试仍未在背包里找到新画布，跳过清空");
			} else {
				this.clearAfterCreateTicks = 10;
			}
		}

		this.suppressNativeMenu(client);

		if (this.stale.isEmpty()) {
			return;
		}

		int delay = Math.max(1, MapDrawConfig.get().resyncDelayTicks);

		if (this.tickCounter % delay != 0) {
			return;
		}

		List<String> pending = new ArrayList<>(this.stale);
		this.stale.clear();

		for (String id : pending) {
			MapDrawClientNetworking.requestCanvas(id);
		}
	}

	/** 0x80 回执。 */
	public void onResponse(int originalPacketId, boolean success, String message) {
		String text = message == null || message.isEmpty() ? "(无提示)" : message;

		if (success) {
			this.setStatus(MapDrawProtocol.packetName(originalPacketId) + " 成功: " + text, 0xFF55FF55);
		} else {
			this.setStatus(MapDrawProtocol.packetName(originalPacketId) + " 失败: " + text, 0xFFFF5555);

			// 只有「绘制类」操作失败才需要重拉像素；0x0C 自己失败还去重拉会变成死循环
			if (originalPacketId >= MapDrawProtocol.C2S_DRAW_PIXEL
					&& originalPacketId <= MapDrawProtocol.C2S_SET_META
					&& !this.currentId.isEmpty()) {
				this.markStale(this.currentId);
			}
		}

		if (MapDrawConfig.get().selfTest) {
			MapDrawClient.LOGGER.info("[SelfTest] 收到 0x80 回执: original={} success={} message={}",
					MapDrawProtocol.packetName(originalPacketId), success, text);
		}

		// 0x08 建完画布后，服务端会把它整张填成 default_bg_color（默认白）。
		// 记下建之前的已知画布，稍后把新出现的那张清成透明。
		if (success && originalPacketId == MapDrawProtocol.C2S_CREATE_CANVAS
				&& MapDrawConfig.get().clearNewCanvas) {
			this.knownIdsBeforeCreate.clear();
			this.knownIdsBeforeCreate.addAll(this.byId.keySet());
			this.knownIdsBeforeCreate.addAll(this.seenCanvasIds);
			this.clearAfterCreate = true;
			this.clearAfterCreateAttempts = 0;
			this.clearAfterCreateTicks = 10;
		}

		if (MapDrawConfig.get().debugPacketLog) {
			MapDrawClient.LOGGER.info("[MapDrawClient] 回执 {} success={} msg={}",
					MapDrawProtocol.packetName(originalPacketId), success, text);
		}

		// 没有打开任何界面时，把关键反馈也送到聊天栏
		Minecraft client = Minecraft.getInstance();

		if (client.player != null && top.colorgarden.mapdrawclient.compat.Compat.screen(client) == null) {
			top.colorgarden.mapdrawclient.compat.Compat.sendMessage(client.player, Component.literal("[MapDraw] " + text));
		}
	}

	/** 0x81 画布全量同步。 */
	public void onSync(CanvasData canvas) {
		canvas.markSynced();
		this.byId.put(canvas.id(), canvas);
		boolean firstSeen = this.seenCanvasIds.add(canvas.id());

		// 刚建好的画布如果同步下来的内容是「整张同一种颜色」，那一定是插件填的
		// default_bg_color 白底（客户端自己的清空包是按逻辑格发的橡皮擦，不会留下纯色）。
		// 这条兜底不依赖背包探测，所以即使 0x08 回执比地图物品早到也能清干净。
		if (firstSeen && this.clearAfterCreate && MapDrawConfig.get().clearNewCanvas
				&& !this.knownIdsBeforeCreate.contains(canvas.id())) {
			int uniform = canvas.uniformFill();

			if (uniform >= 0) {
				MapDrawClient.LOGGER.info("[MapDrawClient] 新画布 {} 同步下来是纯色 ({}), 判定为插件白底，立即清空",
						canvas.id(), uniform);
				this.sendClear(canvas.id(), canvas.size());
				this.knownIdsBeforeCreate.add(canvas.id());
				this.clearAfterCreate = false;
			} else {
				MapDrawClient.LOGGER.info("[MapDrawClient] 新画布 {} 同步下来已经是有内容的图（非纯色），不做清空",
						canvas.id());
				this.knownIdsBeforeCreate.add(canvas.id());
				this.clearAfterCreate = false;
			}
		}

		if (MapDrawConfig.get().selfTest) {
			MapDrawClient.LOGGER.info("[SelfTest] 收到 0x81 CANVAS_DATA_SYNC: id={} mapId={} size={} 保护={} 已落笔像素={}",
					canvas.id(), canvas.mapId(), canvas.size(), canvas.isProtected(), this.countPainted(canvas));
		}

		if (canvas.mapId() != 0) {
			this.idByMapId.put(canvas.mapId(), canvas.id());
		}

		if (this.currentId.isEmpty()) {
			this.setCurrent(canvas.id());
		}

		this.setStatus("已同步画布: " + canvas.displayName()
				+ " (" + canvas.logicalSize() + "x" + canvas.logicalSize() + " 逻辑格, 每格 "
				+ canvas.gridN() + "px"
				+ (canvas.isProtected() ? ", 已保护" : "")
				+ (canvas.animated() ? ", 动图" : "") + ")", 0xFF55FFFF);

		// 有挂起的操作（油漆桶这种结果由服务端决定的操作）时，同步回来就结算成一步本地历史
		EditHistory pendingHistory = this.histories.get(canvas.id());

		if (pendingHistory != null && pendingHistory.hasPending() && pendingHistory.commit(canvas.pixels())) {
			this.setStatus("已记录一步服务端落笔（本地撤销栈 " + pendingHistory.undoDepth() + " 步，Ctrl+Z 撤销）",
					0xFF55FF55);
		}

		Minecraft client = Minecraft.getInstance();

		if (MapDrawConfig.get().autoOpenBoardOnSync
				&& this.screenGuardTicks <= 0
				&& client.player != null
				&& top.colorgarden.mapdrawclient.compat.Compat.screen(client) == null
				&& this.autoOpened.add(canvas.id())) {
			top.colorgarden.mapdrawclient.compat.Compat.setScreen(client, new BoardScreen(canvas.id()));
		}
	}

	// ------------------------------------------------------------------
	// 本地撤销 / 重做历史
	// ------------------------------------------------------------------

	/** 取某张画布的本地历史（按需创建）。 */
	public EditHistory history(String canvasId) {
		if (canvasId == null || canvasId.isEmpty()) {
			return EMPTY_HISTORY;
		}

		return this.histories.computeIfAbsent(canvasId, id -> new EditHistory());
	}

	/** 有没有本地历史里还挂着一个「等同步回来才提交」的操作（例如油漆桶）。 */
	public boolean hasPendingHistory(String canvasId) {
		EditHistory history = this.histories.get(canvasId == null ? "" : canvasId);
		return history != null && history.hasPending();
	}

	// ------------------------------------------------------------------
	// 查询 / 修改
	// ------------------------------------------------------------------

	public Collection<CanvasData> all() {
		return this.byId.values();
	}

	public boolean isEmpty() {
		return this.byId.isEmpty();
	}

	public CanvasData get(String id) {
		return id == null || id.isEmpty() ? null : this.byId.get(id);
	}

	public CanvasData current() {
		CanvasData canvas = this.get(this.currentId);

		if (canvas == null && !this.byId.isEmpty()) {
			canvas = this.byId.values().iterator().next();
		}

		return canvas;
	}

	public String currentId() {
		return this.currentId;
	}

	public void setCurrent(String id) {
		this.currentId = id == null ? "" : id;
		MapDrawConfig.get().lastCanvasId = this.currentId;
		MapDrawConfig.save();
	}

	public CanvasData byMapId(int mapId) {
		String id = this.idByMapId.get(mapId);
		return id == null ? null : this.get(id);
	}

	/** 标记某画布需要重新拉取 (撤销/重做/批量绘制后使用)。 */
	public void markStale(String id) {
		if (id != null && !id.isEmpty()) {
			this.stale.add(id);
		}
	}

	public void setStatus(String text, int argb) {
		this.status = text == null ? "" : text;
		this.statusColor = argb;
		this.statusAt = System.currentTimeMillis();
	}

	public String status() {
		return this.status;
	}

	public int statusColor() {
		return this.statusColor;
	}

	/** 状态文本是否还“新鲜”(用于高亮渐隐)。 */
	public boolean statusFresh(long millis) {
		return System.currentTimeMillis() - this.statusAt < millis;
	}

	/** 本地乐观落笔：先画上去保证手感，失败时靠重同步纠正。 */
	public boolean applyLocalPixel(String canvasId, int x, int y, byte value) {
		CanvasData canvas = this.get(canvasId);

		if (canvas == null) {
			return false;
		}

		canvas.setPixel(x, y, value);
		canvas.markLocalEdit();
		return true;
	}

	/**
	 * 本地乐观落笔（整格）：服务端一次落笔填的是 {@code gridN x gridN} 个地图像素，
	 * 客户端本地预览按整格涂色，两边看起来才一致。
	 *
	 * @param x     逻辑格左上角 X（地图像素）
	 * @param y     逻辑格左上角 Y
	 * @param gridN 逻辑格边长
	 */
	public boolean fillLocalCell(String canvasId, int x, int y, int gridN, byte value) {
		CanvasData canvas = this.get(canvasId);

		if (canvas == null) {
			return false;
		}

		int n = Math.max(1, gridN);

		for (int dy = 0; dy < n; dy++) {
			for (int dx = 0; dx < n; dx++) {
				canvas.setPixel(x + dx, y + dy, value);
			}
		}

		canvas.markLocalEdit();
		return true;
	}

	public String selfName() {
		Minecraft client = Minecraft.getInstance();
		return client.getUser() == null ? "" : client.getUser().getName();
	}
}
