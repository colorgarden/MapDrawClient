package top.colorgarden.mapdrawclient.ui;

import top.colorgarden.mapdrawclient.compat.Compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
//#if MC >= 12110
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//#endif
import net.minecraft.network.chat.Component;

/**
 * 模组内所有界面的基类。
 *
 * <p>按钮/输入框都是自绘的（不继承原版 Widget），只有 {@link Screen} 的生命周期、
 * 渲染与输入回调依赖原版 API：
 * {@code init()} / {@code extractRenderState(...)} / {@code mouseClicked} /
 * {@code mouseReleased} / {@code keyPressed} / {@code charTyped} / {@code onClose()}。</p>
 */
public abstract class MapDrawScreen extends Screen {
	protected final List<UiButton> buttons = new ArrayList<>();
	protected final List<UiField> fields = new ArrayList<>();
	protected UiField focusedField;
	protected int mouseX;
	protected int mouseY;
	protected float partialTick;

	protected MapDrawScreen(Component title) {
		super(title);
		// 记住「刚打开过客户端界面」：同步回来时不要用画板把当前界面顶掉
		top.colorgarden.mapdrawclient.canvas.CanvasStore.INSTANCE.noteClientScreenOpened();
	}

	// ------------------------------------------------------------------
	// 生命周期
	// ------------------------------------------------------------------
	/**
	 * 窗口缩放/改变 GUI 缩放时，原版会重新调用 init()。
	 *
	 * <p>这里必须先清空自绘控件列表，否则旧按钮会残留在列表里、
	 * 和新按钮一起被画出来，出现「右侧面板两份重叠」的现象。</p>
	 */
	@Override
	protected void init() {
		this.buttons.clear();
		this.fields.clear();
		this.focusedField = null;
		super.init();
	}

	// ------------------------------------------------------------------
	// 控件模型
	// ------------------------------------------------------------------
	public static final class UiButton {
		public int x;
		public int y;
		public int w;
		public int h;
		public Supplier<String> label = () -> "";
		public UiIcon icon = UiIcon.NONE;
		public Runnable action = () -> {
		};
		public BooleanSupplier enabled = () -> true;
		public BooleanSupplier selected;
		public String tooltip = "";
		public boolean hovered;
	}

	public static final class UiField {
		public int x;
		public int y;
		public int w;
		public int h;
		public String label = "";
		public String value = "";
		public String hint = "";
		public int maxLength = 128;
		public boolean digitsOnly;
		public int cursor;
		public boolean hovered;
	}

	protected UiButton addButton(int x, int y, int w, int h, String label, Runnable action) {
		return this.addButton(x, y, w, h, UiIcon.NONE, label, action);
	}

	protected UiButton addButton(int x, int y, int w, int h, UiIcon icon, String label, Runnable action) {
		UiButton button = new UiButton();
		button.x = x;
		button.y = y;
		button.w = w;
		button.h = h;
		button.icon = icon;
		button.label = () -> label;
		button.action = action;
		this.buttons.add(button);
		return button;
	}

	protected UiField addField(int x, int y, int w, int h, String label, String value, int maxLength) {
		UiField field = new UiField();
		field.x = x;
		field.y = y;
		field.w = w;
		field.h = h;
		field.label = label;
		field.value = value == null ? "" : value;
		field.maxLength = maxLength;
		field.cursor = field.value.length();
		this.fields.add(field);
		return field;
	}

	// ------------------------------------------------------------------
	// 渲染
	// ------------------------------------------------------------------
	//#if MC >= 260102
	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		this.renderInternal(graphics, mouseX, mouseY, delta);
	}
	//#else
	//$$ @Override
	//$$ public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
	//$$ 	super.render(graphics, mouseX, mouseY, delta);
	//$$ 	this.renderInternal(graphics, mouseX, mouseY, delta);
	//$$ }
	//#endif

	/** 真正的绘制逻辑（版本无关，两个入口都走这里）。 */
	private void renderInternal(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		this.partialTick = delta;

		graphics.fill(0, 0, this.width, this.height, UiKit.SCRIM);
		renderScreen(graphics, mouseX, mouseY, delta);

		for (UiButton button : this.buttons) {
			button.hovered = UiKit.contains(button.x, button.y, button.w, button.h, mouseX, mouseY);
			boolean active = button.enabled.getAsBoolean();

			if (button.selected != null) {
				UiKit.toggleButton(graphics, this.font, button.x, button.y, button.w, button.h,
						button.icon, button.label.get(), button.selected.getAsBoolean(), button.hovered);
			} else {
				UiKit.iconButton(graphics, this.font, button.x, button.y, button.w, button.h,
						button.icon, button.label.get(), button.hovered, active);
			}
		}

		for (UiField field : this.fields) {
			field.hovered = UiKit.contains(field.x, field.y, field.w, field.h, mouseX, mouseY);

			if (field.value.isEmpty() && field.hint != null && !field.hint.isEmpty() && field != this.focusedField) {
				UiKit.field(graphics, this.font, field.x, field.y, field.w, field.h, "", field.label, false, 0);
				Compat.text(graphics, this.font, UiKit.ellipsize(this.font, field.hint, field.w - 6), field.x + 3,
						field.y + (field.h - 8) / 2, UiKit.TEXT_MUTED, false);
			} else {
				UiKit.field(graphics, this.font, field.x, field.y, field.w, field.h,
						field.value, field.label, field == this.focusedField, field.cursor);
			}
		}

		for (UiButton button : this.buttons) {
			if (button.hovered && button.tooltip != null && !button.tooltip.isEmpty()) {
				UiKit.tooltip(graphics, this.font, button.tooltip, mouseX, mouseY, this.width, this.height);
				break;
			}
		}

		renderOverlay(graphics, mouseX, mouseY, delta);
	}

	/** 子类绘制界面内容。 */
	protected abstract void renderScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta);

	/** 在按钮/输入框之后绘制的覆盖层 (气泡、提示等)。 */
	protected void renderOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
	}

	// ------------------------------------------------------------------
	// 输入
	// ------------------------------------------------------------------
	//#if MC >= 12110
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.clickInternal((int) event.x(), (int) event.y(), event.button())) {
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}
	//#else
	//$$ @Override
	//$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
	//$$ 	if (this.clickInternal((int) mouseX, (int) mouseY, button)) {
	//$$ 		return true;
	//$$ 	}
	//$$
	//$$ 	return super.mouseClicked(mouseX, mouseY, button);
	//$$ }
	//#endif

	/** 版本无关的点击处理。 */
	private boolean clickInternal(int mx, int my, int button) {
		for (UiField field : this.fields) {
			if (UiKit.contains(field.x, field.y, field.w, field.h, mx, my)) {
				this.focusedField = field;
				field.cursor = field.value.length();
				return true;
			}
		}

		this.focusedField = null;

		for (UiButton uiButton : this.buttons) {
			if (uiButton.enabled.getAsBoolean() && UiKit.contains(uiButton.x, uiButton.y, uiButton.w, uiButton.h, mx, my)) {
				uiButton.action.run();
				return true;
			}
		}

		return onMouseClick(mx, my, button);
	}

	//#if MC >= 12110
	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (onMouseRelease((int) event.x(), (int) event.y(), event.button())) {
			return true;
		}

		return super.mouseReleased(event);
	}
	//#else
	//$$ @Override
	//$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
	//$$ 	if (onMouseRelease((int) mouseX, (int) mouseY, button)) {
	//$$ 		return true;
	//$$ 	}
	//$$
	//$$ 	return super.mouseReleased(mouseX, mouseY, button);
	//$$ }
	//#endif

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return onMouseScroll((int) mouseX, (int) mouseY, scrollY);
	}

	//#if MC >= 12110
	@Override
	public boolean keyPressed(KeyEvent event) {
		// 注册过的按键绑定优先（这样界面内快捷键也能在「选项 → 控制」里改）
		if (this.onKeyEvent(event)) {
			return true;
		}

		return this.keyInternal(event.key(), event.scancode(), event.modifiers());
	}
	//#else
	//$$ @Override
	//$$ public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
	//$$ 	return this.keyInternal(keyCode, scanCode, modifiers);
	//$$ }
	//#endif

	/** 版本无关的按键处理（按键绑定的派发见 MapDrawKeys）。 */
	private boolean keyInternal(int keyCode, int scanCode, int modifiers) {
		boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
		boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

		if (this.focusedField != null) {
			UiField field = this.focusedField;
			int cursor = UiKit.clamp(field.cursor, 0, field.value.length());

			switch (keyCode) {
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (cursor > 0) {
						field.value = field.value.substring(0, cursor - 1) + field.value.substring(cursor);
						field.cursor = cursor - 1;
						onFieldChanged(field);
					}

					return true;
				}
				case GLFW.GLFW_KEY_DELETE -> {
					if (ctrl) {
						field.value = "";
						field.cursor = 0;
					} else if (cursor < field.value.length()) {
						field.value = field.value.substring(0, cursor) + field.value.substring(cursor + 1);
					}

					onFieldChanged(field);
					return true;
				}
				case GLFW.GLFW_KEY_LEFT -> {
					field.cursor = Math.max(0, cursor - (ctrl ? 8 : 1));
					return true;
				}
				case GLFW.GLFW_KEY_RIGHT -> {
					field.cursor = Math.min(field.value.length(), cursor + (ctrl ? 8 : 1));
					return true;
				}
				case GLFW.GLFW_KEY_HOME -> {
					field.cursor = 0;
					return true;
				}
				case GLFW.GLFW_KEY_END -> {
					field.cursor = field.value.length();
					return true;
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					onFieldSubmit(field);
					this.focusedField = null;
					return true;
				}
				case GLFW.GLFW_KEY_ESCAPE -> {
					this.focusedField = null;
					return true;
				}
				case GLFW.GLFW_KEY_V -> {
					if (ctrl) {
						String paste = ClipboardHelper.get().replace('\n', ' ').trim();

						if (!paste.isEmpty()) {
							String combined = field.value.substring(0, cursor) + paste + field.value.substring(cursor);

							if (combined.length() > field.maxLength) {
								combined = combined.substring(0, field.maxLength);
							}

							field.value = combined;
							field.cursor = Math.min(combined.length(), cursor + paste.length());
							onFieldChanged(field);
						}

						return true;
					}
				}
				case GLFW.GLFW_KEY_A -> {
					if (ctrl) {
						field.cursor = field.value.length();
						return true;
					}
				}
				default -> {
				}
			}

			// 输入框聚焦时吞掉其它按键 (除了 F 系列等系统键继续交给原版)
			if (keyCode != GLFW.GLFW_KEY_F1 && keyCode != GLFW.GLFW_KEY_F2 && keyCode != GLFW.GLFW_KEY_F3
					&& keyCode != GLFW.GLFW_KEY_F11 && keyCode != GLFW.GLFW_KEY_F5) {
				return true;
			}
		}


		if (onKeyPressed(keyCode, scanCode, ctrl, shift)) {
			return true;
		}

		// 旧版本没有事件对象，只能交给原版的原始签名
		//#if MC >= 12110
		return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
		//#else
		//$$ return super.keyPressed(keyCode, scanCode, modifiers);
		//#endif
	}

	//#if MC >= 12110
	/** 按键绑定回调：子类用 {@code KeyMapping.matches(event)} 判断。 */
	protected boolean onKeyEvent(KeyEvent event) {
		return false;
	}
	//#else
	//$$ /** 按键绑定回调（旧版本只有原始键码）。 */
	//$$ protected boolean onKeyEvent(int keyCode, int scanCode, int modifiers) {
	//$$ 	return false;
	//$$ }
	//#endif

	//#if MC >= 12110
	@Override
	public boolean charTyped(CharacterEvent event) {
		return this.charInternal(event.codepointAsString(), super.charTyped(event));
	}
	//#else
	//$$ @Override
	//$$ public boolean charTyped(char chr, int modifiers) {
	//$$ 	return this.charInternal(String.valueOf(chr), super.charTyped(chr, modifiers));
	//$$ }
	//#endif

	/** 版本无关的字符输入；{@code fallback} 是交给原版的结果。 */
	private boolean charInternal(String insert, boolean fallback) {
		if (this.focusedField != null) {
			UiField field = this.focusedField;
			int codepoint = insert.isEmpty() ? 0 : insert.codePointAt(0);

			if (codepoint >= 32 && codepoint != 127 && codepoint != 167) {
				int cursor = UiKit.clamp(field.cursor, 0, field.value.length());
				String combined = field.value.substring(0, cursor) + insert + field.value.substring(cursor);

				if (combined.length() > field.maxLength) {
					combined = combined.substring(0, field.maxLength);
				}

				if (!field.digitsOnly || this.allDigits(combined)) {
					field.value = combined;
					field.cursor = Math.min(combined.length(), cursor + insert.length());
					onFieldChanged(field);
				}
			}

			return true;
		}

		return fallback;
	}

	private boolean allDigits(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isDigit(text.charAt(i))) {
				return false;
			}
		}

		return true;
	}

	// ---- 子类钩子 ----
	protected boolean onMouseClick(int x, int y, int button) {
		return false;
	}

	protected boolean onMouseRelease(int x, int y, int button) {
		return false;
	}

	protected boolean onMouseScroll(int x, int y, double amount) {
		return false;
	}

	protected boolean onKeyPressed(int keyCode, int scanCode, boolean ctrl, boolean shift) {
		return false;
	}

	protected void onFieldChanged(UiField field) {
	}

	protected void onFieldSubmit(UiField field) {
	}

	/** 关闭时保存一次配置；有父界面则返回父界面。 */
	@Override
	public void onClose() {
		top.colorgarden.mapdrawclient.MapDrawConfig.save();
		top.colorgarden.mapdrawclient.compat.Compat.setScreen(this.minecraft, this.parent);
	}

	/** 返回父界面 (null = 回到游戏)。 */
	protected Screen parent;

	public MapDrawScreen setParent(Screen parent) {
		this.parent = parent;
		return this;
	}

	/** 可选：签名若变动也不会导致编译失败。 */
	public boolean isPauseScreen() {
		return false;
	}

	protected void open(Screen screen) {
		top.colorgarden.mapdrawclient.compat.Compat.setScreen(this.minecraft, screen);
	}
}
