package top.colorgarden.mapdrawclient.ui;

import top.colorgarden.mapdrawclient.compat.Compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.io.File;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasData;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.net.ImageHostUploader;

/**
 * 上传图片界面，两种模式：
 *
 * <ol>
 *   <li><b>服务器上传</b>：填图片 URL，客户端发一条 {@code /mdw upload <url> <dither|none> <宽> <高>}
 *       给服务端，由插件的 {@code ImageProcessUtil} 下载、量化、切多联画并扣费
 *       （需要 {@code mapdraw.upload} 权限，受插件 config 的 upload.max_* 限制）。</li>
 *   <li><b>本地文件</b>：填本地图片路径，客户端把文件传到免费图床拿到 URL，
 *       再走同一条 {@code /mdw upload} 命令 —— <b>客户端不做任何图片处理</b>，
 *       缩放/量化/抖动/多联画/扣费全在插件那边完成。</li>
 * </ol>
 */
public class UploadScreen extends MapDrawScreen {
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int section1Y;
	private int section2Y;
	private int hintY;

	private UiField urlField;
	private UiField widthField;
	private UiField heightField;
	private UiField pathField;

	private boolean dither = true;
	private UiButton hostButton;
	private String hint = "两种方式最后都走插件的 /mdw upload：服务器下载 + 量化 + 切多联画 + 扣费。";

	public UploadScreen(Screen parent) {
		super(Component.literal("上传图片"));
		this.setParent(parent);
	}

	@Override
	protected void init() {
		super.init();

		this.panelW = Math.min(this.width - 16, 420);
		// 面板高度按内容固定算（每个输入框上方 10px 要留给它的 label，段标题再留 10px）
		this.panelH = Math.min(232, this.height - 8);
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = (this.height - this.panelH) / 2;

		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;
		int buttonW = 82;

		// ---- 服务器上传 ----
		this.section1Y = this.panelY + 34;
		int y = this.section1Y + 22;
		this.urlField = this.addField(x0, y, inner - buttonW - 4, 16, "图片 URL", "", 220);
		this.urlField.hint = "https://... 由服务端下载处理";
		this.addButton(x0 + inner - buttonW, y, buttonW, 16, UiIcon.CHECK, "服务器上传", this::uploadByUrl)
				.tooltip = "发送 /mdw upload 命令（需要 mapdraw.upload 权限）";

		y += 30;
		this.widthField = this.addField(x0, y, 52, 16, "宽", "1", 3);
		this.widthField.digitsOnly = true;
		this.heightField = this.addField(x0 + 58, y, 52, 16, "高", "1", 3);
		this.heightField.digitsOnly = true;

		UiButton algo = this.addButton(x0 + 118, y, inner - 118, 16, UiIcon.GRID, "抖动 (dither)", this::toggleDither);
		algo.selected = () -> this.dither;
		algo.tooltip = "Floyd–Steinberg 误差扩散：用光学混色突破调色板限制";

		// ---- 本地文件：上传到免费图床，再交给插件处理（客户端不做图片处理）----
		this.section2Y = y + 34;
		y = this.section2Y + 22;
		this.pathField = this.addField(x0, y, inner - buttonW - 4, 16, "本地图片路径", "", 260);
		this.pathField.hint = "C:\\pictures\\cat.png （png / jpg / gif / bmp）";
		this.addButton(x0 + inner - buttonW, y, buttonW, 16, UiIcon.NEW, "清空输入", () -> this.pathField.value = "")
				.tooltip = "清空路径输入框";

		y += 30;
		this.hostButton = this.addButton(x0, y, inner - 70, 16, UiIcon.CHECK, this.hostLabel(), this::uploadToImageHost);
		this.hostButton.label = this::hostLabel;
		this.hostButton.tooltip = "把本地图片传到免费图床拿到 URL，再让插件处理（支持多联画/抖动/扣费）";

		UiButton switchHost = this.addButton(x0 + inner - 66, y, 66, 16, UiIcon.SYNC, "换图床", this::cycleHost);
		switchHost.tooltip = "在 catbox / uguu / 0x0 之间切换";

		this.hintY = y + 24;
		this.addButton(x0, this.panelY + this.panelH - 24, inner, 16, "返回", this::onClose);
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiKit.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
		UiKit.header(g, this.font, this.panelX + 4, this.panelY + 4, this.panelW - 8, "上传图片");

		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;

		CanvasData canvas = CanvasStore.INSTANCE.current();
		String info = canvas == null
				? "当前没有画布：先选中一张画布，上传结果会画到它上面"
				: ("当前画布: " + canvas.displayName() + "  " + canvas.logicalSize() + "x" + canvas.logicalSize()
						+ " 逻辑格"
						+ (canvas.isProtected() ? "  （已保护，服务端会拒绝）" : ""));
		Compat.text(g, this.font, UiKit.ellipsize(this.font, info, inner), x0, this.panelY + 18,
				canvas == null ? UiKit.WARN : UiKit.TEXT_DIM, false);

		UiKit.header(g, this.font, x0, this.section1Y, inner, "服务器上传（插件处理，可多联画）");
		UiKit.header(g, this.font, x0, this.section2Y, inner, "本地文件 → 免费图床 → 交给插件");

		Compat.text(g, this.font, UiKit.ellipsize(this.font, this.hint, inner), x0, this.hintY,
				UiKit.TEXT_MUTED, false);
	}

	private String hostLabel() {
		return "上传到图床并交给插件（" + MapDrawConfig.get().imageHost + "）";
	}

	/** 换一个图床（点按钮循环切换）。 */
	private void cycleHost() {
		String[] hosts = ImageHostUploader.HOSTS;
		String current = MapDrawConfig.get().imageHost;
		int index = 0;

		for (int i = 0; i < hosts.length; i++) {
			if (hosts[i].equalsIgnoreCase(current)) {
				index = i;
			}
		}

		MapDrawConfig.get().imageHost = hosts[(index + 1) % hosts.length];
		MapDrawConfig.save();
	}

	/** 本地文件 → 免费图床 → /mdw upload。 */
	private void uploadToImageHost() {
		String path = this.pathField.value.trim();

		if (path.isEmpty()) {
			CanvasStore.INSTANCE.setStatus("请先填本地图片路径", UiKit.WARN);
			return;
		}

		File file = new File(path);

		if (!file.isFile()) {
			CanvasStore.INSTANCE.setStatus("文件不存在: " + path, UiKit.ERR);
			return;
		}

		final String host = MapDrawConfig.get().imageHost;
		this.hint = "正在上传到图床 (" + host + ") …";
		CanvasStore.INSTANCE.setStatus("开始上传到图床", UiKit.TEXT);

		ImageHostUploader.upload(file, host, (url, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				this.hint = "图床上传失败: " + error;
				CanvasStore.INSTANCE.setStatus("图床上传失败（可点按钮换一个图床）", UiKit.ERR);
				return;
			}

			int width = parseInt(this.widthField.value, 1);
			int height = parseInt(this.heightField.value, 1);
			String command = "mdw upload " + url + " " + (this.dither ? "dither" : "none")
					+ " " + width + " " + height;
			ClientPacketListener connection = Minecraft.getInstance().getConnection();

			if (connection == null) {
				this.hint = "图床链接已拿到，但游戏未连接服务器: " + url;
				CanvasStore.INSTANCE.setStatus("未连接服务器", UiKit.ERR);
				return;
			}

			connection.sendCommand(command);
			this.hint = "图床链接: " + url + "  → 已发送 /" + command;
			CanvasStore.INSTANCE.setStatus("已交给插件处理，结果看聊天栏", UiKit.OK);
		}));
	}

	// ------------------------------------------------------------------
	private void toggleDither() {
		this.dither = !this.dither;
	}

	private void uploadByUrl() {
		String url = this.urlField.value.trim();

		if (url.isEmpty()) {
			CanvasStore.INSTANCE.setStatus("请输入图片 URL", UiKit.WARN);
			return;
		}

		// 命令是按空格切分的，URL 里不能带空格
		String safeUrl = url.replace(" ", "%20");
		int width = parseInt(this.widthField.value, 1);
		int height = parseInt(this.heightField.value, 1);
		String command = "mdw upload " + safeUrl + " " + (this.dither ? "dither" : "none")
				+ " " + width + " " + height;
		ClientPacketListener connection = Minecraft.getInstance().getConnection();

		if (connection == null) {
			CanvasStore.INSTANCE.setStatus("未连接到服务器", UiKit.ERR);
			return;
		}

		connection.sendCommand(command);
		this.hint = "已发送命令: /" + command + " —— 结果看聊天栏";
		CanvasStore.INSTANCE.setStatus("已请求服务端上传处理", UiKit.OK);
	}

	private int parseInt(String text, int fallback) {
		try {
			int value = Integer.parseInt(text.trim());
			return Math.max(1, Math.min(64, value));
		} catch (Exception e) {
			return fallback;
		}
	}
}
