package top.colorgarden.mapdrawclient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import top.colorgarden.mapdrawclient.canvas.MapPalette;

/**
 * 客户端配置，保存于 {@code config/mapdrawclient.json}。
 */
public final class MapDrawConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static MapDrawConfig instance;

	// ---- 行为 ----
	/** 收到 0x81 画布同步且当前没有打开任何界面时，自动打开画板。 */
	public boolean autoOpenBoardOnSync = true;

	/** 拖拽时单个 0x02 批量包最多包含多少个点。 */
	public int batchFlushPoints = 64;

	/** 撤销/重做/保护等操作后，延迟多少 tick 主动 0x0C 拉取一次最新像素。 */
	public int resyncDelayTicks = 3;

	/** 打开画板时自动 0x0C 请求画布数据。 */
	public boolean requestOnOpen = true;

	/** 打印收发的原始包 (调试用)。 */
	public boolean debugPacketLog = false;

	/** 联调自检：进服后自动检查通道可用性、手持地图 PDC 与 0x0C/0x80 往返。 */
	public boolean selfTest = false;

	/** 蹲下空手右键「放着画布地图的展示框」也打开画板（不想要可关）。 */
	public boolean openBoardOnFrameClick = true;

	/** 手持画布地图右键是否直接打开画板。 */
	public boolean openBoardOnRightClick = true;

	/** 浅色主题（false = 深色主题）。 */
	public boolean lightTheme = false;

	/** 开发用：进服后自动打开控制台菜单（方便截图核对界面，默认关）。 */
	public boolean openMenuOnJoin = false;

	/** 免费图床：catbox / uguu / 0x0（用于把本地图片传成 URL 再交给插件处理）。 */
	public String imageHost = "catbox";

	/** 兜底拦截：插件自己弹出来的原生菜单（玩家没主动要的）直接换成客户端界面。 */
	public boolean suppressPluginMenu = true;

	/**
	 * 新建画布后自动清空成透明。
	 *
	 * <p>插件会用 {@code canvas.default_bg_color}（默认 34 = 白色）把整张 128x128 填满，
	 * 所以刚建好的画布在游戏里看是一张全白地图。客户端建的画布会被清成透明，
	 * 想要白底就把它关掉。</p>
	 */
	public boolean clearNewCanvas = true;

	// ---- 界面状态 ----
	/** 是否显示像素网格。 */
	public boolean showGrid = true;

	/**
	 * 缩放自动适配：让整张 128x128 画布铺满画布视口。
	 *
	 * <p>注意 {@code size}（16/32/64/128）是逻辑网格分辨率，不是像素区大小：
	 * 任何尺寸下底层都是整张 128x128 地图，{@code size=16} 表示一个逻辑像素
	 * 占 8x8 个地图像素。所以自动适配按 128 算，不按 size 算。</p>
	 */
	public boolean autoFit = true;

	/** 缩放倍率 (1 像素 = N 个 GUI 单位)。 */
	public int zoom = 1;

	/** 当前工具：0=画笔 1=橡皮 2=油漆桶 3=无。 */
	public int tool = 0;

	/** 当前颜色 (MapColor 字节)。 */
	public int color = 114;

	/** 最近一次使用/同步的画布 ID。 */
	public String lastCanvasId = "";

	/** 新建画布对话框里的名字。 */
	public String newCanvasName = "我的画作";

	/** 新建画布对话框里的尺寸。 */
	public int newCanvasSize = 128;

	/** 是否显示透明像素的棋盘格底纹。 */
	public boolean showCheckerboard = true;

	/** 最近用过的颜色（地图颜色字节，最新在前）。 */
	public java.util.List<Integer> historyColors = new java.util.ArrayList<>();

	/** 历史颜色最多保留多少个。 */
	public static final int HISTORY_MAX = 18;

	/**
	 * 记一个最近使用的颜色：去重、最新在前、超长裁掉最旧的。
	 *
	 * <p>只在<b>真的拿它画了东西</b>时才调用（不是选中就记），否则拖一下 RGB 滑块
	 * 就会把一堆没确认的颜色塞进历史。</p>
	 *
	 * @return 历史是否真的变了（变了才需要存盘）
	 */
	public boolean pushHistoryColor(int colorByte) {
		if (colorByte < 0 || colorByte > 255) {
			return false;
		}

		if (this.historyColors == null) {
			this.historyColors = new java.util.ArrayList<>();
		}

		if (!this.historyColors.isEmpty() && this.historyColors.get(0) == colorByte) {
			return false;
		}

		this.historyColors.remove(Integer.valueOf(colorByte));
		this.historyColors.add(0, colorByte);

		while (this.historyColors.size() > HISTORY_MAX) {
			this.historyColors.remove(this.historyColors.size() - 1);
		}

		return true;
	}

	public static MapDrawConfig get() {
		if (instance == null) {
			load();
		}

		return instance;
	}

	public static void load() {
		Path file = filePath();
		MapDrawConfig loaded = null;

		try {
			if (Files.exists(file)) {
				String json = Files.readString(file, StandardCharsets.UTF_8);
				loaded = GSON.fromJson(json, MapDrawConfig.class);
			}
		} catch (Exception e) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] 读取配置失败，使用默认值: {}", e.toString());
		}

		if (loaded == null) {
			loaded = new MapDrawConfig();
		}

		loaded.sanitize();
		instance = loaded;
	}

	public static void save() {
		if (instance == null) {
			return;
		}

		Path file = filePath();

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MapDrawClient.LOGGER.warn("[MapDrawClient] 保存配置失败: {}", e.toString());
		}
	}

	private static Path filePath() {
		return FabricLoader.getInstance().getConfigDir().resolve("mapdrawclient.json");
	}

	private void sanitize() {
		if (batchFlushPoints < 1) {
			batchFlushPoints = 1;
		}

		if (batchFlushPoints > 4096) {
			batchFlushPoints = 4096;
		}

		if (resyncDelayTicks < 0) {
			resyncDelayTicks = 0;
		}

		if (zoom < 1) {
			zoom = 1;
		}

		if (zoom > 32) {
			zoom = 32;
		}

		if (tool < 0 || tool > 3) {
			tool = 0;
		}

		if (!MapPalette.isValid((byte) color)) {
			color = 114;
		}

		if (newCanvasSize != 16 && newCanvasSize != 32 && newCanvasSize != 64 && newCanvasSize != 128) {
			newCanvasSize = 128;
		}

		if (lastCanvasId == null) {
			lastCanvasId = "";
		}

		if (newCanvasName == null || newCanvasName.isEmpty()) {
			newCanvasName = "我的画作";
		}

		if (historyColors == null) {
			historyColors = new java.util.ArrayList<>();
		}

		historyColors.removeIf(v -> v == null || v < 0 || v > 255);

		while (historyColors.size() > HISTORY_MAX) {
			historyColors.remove(historyColors.size() - 1);
		}
	}
}
