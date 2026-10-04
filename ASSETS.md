# MapDrawClient 贴图素材需求清单

> 结论先说：**这个 Mod 目前的界面是纯矢量绘制的（只用 `fill` / `outline` / `text`），不缺任何贴图也能正常运行。**
> 下面这些 PNG 是「可选美化素材」，我已经用 `tools/TextureGenerator.java` 生成了占位图并打包进 `src/main/resources`。
> 你把美术图按同样的路径/尺寸覆盖进去即可，不需要改代码（如果要让代码真正去读它们，见文末「如何接入」）。

---

## 1. 硬需求（必须有的）

| 路径 | 尺寸 | 说明 |
| :--- | :--- | :--- |
| `assets/mapdrawclient/icon.png` | 128×128 PNG | Mod 图标（ModMenu / 模组列表显示）。已生成占位图。 |

除它以外，**没有硬需求**：缺素材最多是「不好看」，不会报错、不会没功能。

---

## 2. 图标素材（16×16 PNG，RGBA，建议 1px 透明内边距）

统一放在 `assets/mapdrawclient/textures/gui/`。风格建议：Minecraft 像素风，硬边、无抗锯齿、无半透明渐变；主色用近白 `#E8E8F0`，强调色用青色 `#55FFFF`，描边用深色 `#101018`。

| 文件名 | 用途 | 当前占位内容 | 备注 |
| :--- | :--- | :--- | :--- |
| `tool_pen.png` | 画板工具：画笔 | 斜向笔刷 | 与 `tool_eraser`/`tool_bucket` 视觉要有区分度 |
| `tool_eraser.png` | 画板工具：橡皮擦 | 斜置橡皮块 | 建议暖色/粉色系 |
| `tool_bucket.png` | 画板工具：油漆桶 | 桶 + 色块 | 对应泛洪填充 |
| `cursor_brush.png` | 画布上的笔刷光标 | 小笔尖 | **热点(hotspot)建议在右下角笔尖** |
| `action_undo.png` | 撤销按钮 | 左弯箭头 | |
| `action_redo.png` | 重做按钮 | 右弯箭头 | |
| `action_sync.png` | 同步（0x0C）按钮 | 双箭头环 | 表示「重新拉取」 |
| `action_lock.png` | 画布已保护状态 | 闭合挂锁 | |
| `action_unlock.png` | 解除保护按钮 | 打开挂锁 | |
| `action_grid.png` | 网格开关 | 九宫格 | 选中态由代码画描边，素材只需一态 |
| `action_new.png` | 新建画布（0x08） | 带加号的纸 | |
| `action_list.png` | 画布列表 | 三行列表 | |
| `action_menu.png` | 服务端菜单（0x0B） | 汉堡菜单 | |
| `action_close.png` | 关闭 | 叉 | |
| `palette.png` | 调色板 / 取色 | 带色点的调色盘 | 出现在多个界面，用得最多 |
| `zoom_in.png` / `zoom_out.png` | 缩放 | 放大镜 ± | |
| `action_check.png` | 确认/创建 | 勾 | |
| `panel_bg.png` | 面板底（可平铺 16×16） | 深色描边底 | 也可做成九宫格切片，见下 |

### 九宫格版本（可选，更好看）
如果你愿意出一套九宫格面板：`panel_bg.png` 做成 16×16（四角各 4px、四边各 8px 可拉伸、中心 8×8 可平铺），
告诉我一声，我把 `UiKit.panel()` 换成 `blit` 九宫格绘制。

| 文件名 | 尺寸 | 用途 |
| :--- | :--- | :--- |
| `textures/gui/panel_bg.png` | 16×16 | 面板/窗口底 |
| `textures/gui/panel_header.png` | 16×16 | 分组标题条（`UiKit.header`） |
| `textures/gui/button.png` / `button_hover.png` / `button_disabled.png` | 16×16 | 按钮三态 |
| `textures/gui/button_selected.png` | 16×16 | 工具/尺寸被选中的高亮态 |
| `textures/gui/slot.png` | 16×16 | 输入框/画布视口底 |
| `textures/gui/checker.png` | 16×16 | 透明像素的棋盘格底纹（8px 格） |

---

## 3. 画布相关的可选素材

| 文件名 | 尺寸 | 说明 |
| :--- | :--- | :--- |
| `textures/gui/canvas_frame.png` | 16×16 九宫格 | 画布视口外框（现在是 `outline` 画的 1px 描边） |
| `textures/gui/bucket_fill_preview.png` | 16×16 | 油漆桶模式下的范围预览图标（可选） |

**不需要的素材**：地图像素本身是运行时从服务端同步的 16384 字节（`0x81`），是数据不是贴图；
调色板的 244 色也是代码里按 MapColor 表算出来的，不需要颜色贴图。

---

## 4. 字体 / 语言

界面文字全部走 MC 字体，无需额外字体文件。当前已提供：

- `assets/mapdrawclient/lang/zh_cn.json`（键位名）
- `assets/mapdrawclient/lang/en_us.json`

如果你要改界面文案（例如把「画板」叫「绘图台」），告诉我，我把硬编码中文抽成翻译键。

---

## 5. 如何用真素材替换占位图

1. 把 PNG 放到上表路径（覆盖同名文件即可，占位图就是我生成的那些）。
2. 重新 `./gradlew build`。
3. 目前代码默认**不读取**这些贴图（所以缺图也不会出错）。要启用贴图渲染，把 `UiIcon.draw(...)` 里对应分支换成一次 blit 即可，26.2 的签名已确认：

```java
// assets/mapdrawclient/textures/gui/tool_pen.png
private static final Identifier ICON_PEN = Identifier.fromNamespaceAndPath("mapdrawclient", "textures/gui/tool_pen.png");

// 在 UiIcon.draw 里：
graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_PEN, x, y, 0.0F, 0.0F, size, size, 16, 16);
```

（`RenderPipelines` 来自 `net.minecraft.client.renderer.RenderPipelines`，
`blit` 的参数顺序为 renderPipeline, texture, x, y, u, v, w, h, texW, texH。）

需要的话我可以直接把整套图标改成贴图驱动 + 缺图自动回退到矢量形状。

---

## 6. 占位图怎么重新生成

```bash
java tools/TextureGenerator.java
```

纯 JDK（无需 Gradle / 网络），会重写 `textures/gui/*.png` 与 `icon.png`。
生成器里的 8×8 ASCII 图案就是每个图标的构图草稿，可以直接给美术参考。
