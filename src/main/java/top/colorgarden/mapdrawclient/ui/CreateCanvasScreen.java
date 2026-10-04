package top.colorgarden.mapdrawclient.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import top.colorgarden.mapdrawclient.MapDrawConfig;
import top.colorgarden.mapdrawclient.canvas.CanvasStore;
import top.colorgarden.mapdrawclient.net.MapDrawClientNetworking;

/**
 * 新建画布：{@code 0x08 CREATE_CANVAS(String name, int size)}。
 * 服务端会校验权限并按配置扣费。
 */
public class CreateCanvasScreen extends MapDrawScreen {
	private static final int[] SIZES = {16, 32, 64, 128};

	private UiField nameField;
	private int size = 128;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;

	public CreateCanvasScreen(Screen parent) {
		super(Component.literal("新建画布"));
		this.setParent(parent);
	}

	@Override
	protected void init() {
		super.init();
		MapDrawConfig cfg = MapDrawConfig.get();
		this.size = cfg.newCanvasSize;

		this.panelW = Math.min(this.width - 20, 260);
		this.panelH = 132;
		this.panelX = (this.width - this.panelW) / 2;
		this.panelY = (this.height - this.panelH) / 2;

		int x0 = this.panelX + 10;
		int inner = this.panelW - 20;

		this.nameField = this.addField(x0, this.panelY + 28, inner, 18, "画布名称",
				cfg.newCanvasName, 40);

		int sizeW = (inner - 6) / 4;
		int y = this.panelY + 68;

		for (int i = 0; i < SIZES.length; i++) {
			final int value = SIZES[i];
			UiButton button = this.addButton(x0 + i * (sizeW + 2), y, sizeW, 18,
					value + "x" + value, () -> {
						this.size = value;
					});
			button.selected = () -> this.size == value;
			button.tooltip = "可用网格 " + value + "x" + value + " (画布底层仍是 128x128 地图)";
		}

		this.addButton(x0, y + 26, inner / 2 - 1, 18, UiIcon.CHECK, "创建画布", this::create);
		this.addButton(x0 + inner / 2 + 1, y + 26, inner / 2 - 1, 18, "取消", this::onClose);
	}

	@Override
	protected void renderScreen(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		UiKit.panel(g, this.panelX, this.panelY, this.panelW, this.panelH);
		UiKit.header(g, this.font, this.panelX + 4, this.panelY + 4, this.panelW - 8, "新建画布");

		// 注意：输入框的 label 是画在框上方 10 像素处的，这一行不能再压上去
		g.text(this.font, "尺寸越大可用网格越细；填色后服务端禁止缩小。", this.panelX + 10, this.panelY + 52,
				UiKit.TEXT_DIM, false);
		g.text(this.font, "费用由服务端 config.yml 的 economy.create_cost 决定。", this.panelX + 10,
				this.panelY + this.panelH - 14, UiKit.TEXT_MUTED, false);
	}

	private void create() {
		String name = this.nameField.value.trim();

		if (name.isEmpty()) {
			name = "我的画作";
		}

		MapDrawConfig cfg = MapDrawConfig.get();
		cfg.newCanvasName = name;
		cfg.newCanvasSize = this.size;
		MapDrawConfig.save();

		MapDrawClientNetworking.createCanvas(name, this.size);
		CanvasStore.INSTANCE.setStatus("已发送创建请求: " + name + " " + this.size + "x" + this.size, UiKit.OK);
		this.onClose();
	}
}
