# MapDraw Client (Fabric, Minecraft 26.2)

配合 Paper 服务端 **MapDraw** 插件使用的 **Fabric 客户端模组**。
它把「拿着笔刷对着展示框一格一格点」的绘制方式，换成 **原生画板 GUI**：一张 128×128 的画布、鼠标拖拽画线、
16 色调色板、撤销/重做、保护、元数据编辑、新建画布——全部通过插件消息通道 `mapdraw:main` 完成，
**所有操作天然绑定发包玩家**，服务端照旧做权限/金币/保护校验。

> 服务端不需要装本模组，也不需要改任何服务端代码；它就是一个「更好的客户端」。

---

## 1. 功能一览

| 功能 | 说明 | 对应端口 |
| :--- | :--- | :--- |
| 画板主界面 | 128×128 地图像素直接可见可点，鼠标左键落笔，拖拽自动连线（Bresenham 补点） | `0x01` / `0x02` |
| 批量画线 | 拖拽时按配置切块批量发包，秒画长线条，无逐点延迟 | `0x02` |
| 撤销 / 重做 | **客户端自己实现**（`Ctrl+Z` / `Ctrl+Y`），按「一次笔画 = 一步」记录像素差异并直接用 `0x01`/`0x02` 写回，不依赖插件那套有缺陷的撤销栈；插件原生的 `0x03`/`0x04` 也保留在面板上 | `0x03` / `0x04`（可选） |
| 保护 / 解除保护 | 一键锁定；本地会立刻进入不可编辑状态 | `0x05` / `0x06` |
| 元数据编辑 | 标题、描述、逻辑尺寸(16/32/64/128)、防拷贝开关 | `0x07` |
| 新建画布 | 名称 + 尺寸，服务端扣费与权限校验照旧；建好后客户端会把插件填的白底清成透明 | `0x08` |
| 工具切换 | 画笔 / 橡皮擦 / 油漆桶 / 无工具；**切换是纯客户端状态**（落笔包自带工具字节） | `0x09`（菜单里显式同步） |
| 笔刷大小 | 画笔与橡皮共用，滑块或 `,` / `.` 调整（1/2/3/4/5/6/8/10/12/16 个逻辑格），悬停预览按实际笔刷大小显示 | — |
| 颜色设置 | 16 色快捷格、画布用色统计、RGB 滑块、`#RRGGBB` / 英文色名 / `&a` 代码解析 | `0x0A` |
| 服务器界面 | 直接请求服务端打开它自己的菜单 / 调色板箱子 | `0x0B` |
| 画布同步 | 请求完整元数据 + 16384 像素并本地缓存渲染 | `0x0C` |
| 回执显示 | `0x80` 的成功/失败提示显示在状态栏，无界面时也会进聊天栏 | `0x80` |
| 画布数据 | `0x81` 全量同步，落库为客户端画布缓存（含 mapId 反查） | `0x81` |

其它细节：

- **尺寸语义（重要）**：`size` 是**逻辑网格分辨率**，不是像素区大小。任何尺寸下底层都是整张
  128×128 地图，`size=16` 表示一个逻辑像素占 `128/16 = 8×8` 个地图像素。所以画板永远显示整张
  128×128 并铺满视口，网格/悬停预览/落笔都按逻辑格吸附（不会再出现「点一下盖掉一大片」）。
- **乐观落笔**：本地先画上去保证手感；服务端返回失败时自动重新拉取真实像素纠正。
- **油漆桶只发单点**：`0x02` 批量包里带 `tool=2` 会让服务端对包内每个点各泛洪一次（一次拖拽就能填满整张），
  客户端强制走 `0x01` 单点。
- **新建画布自动清空**：插件 `createCanvas` 会用 `canvas.default_bg_color`（默认 34 = 白色）把整张
  128×128 填满，客户端建好后按「每个逻辑格一个点」发橡皮擦把它清成透明（配置项 `clearNewCanvas` 可关）。
- **棋盘格相位锚定画布坐标，方块大小按地图像素算**：一格固定等于 `max(4, 128/size)` 个地图像素
  （16x16 画布就是 8 像素 = 一个逻辑格），所以放大缩小不会改变「一格代表多少地图像素」，
  擦/画也不会让图案位移。
- **缩放以鼠标光标为中心**：滚轮 / `+` / `-` 时，光标底下的那个像素保持不动（不是永远以画布中心缩放）。
- **画布可以拖出画板**：中键拖拽最多把画布拖到只剩 24 像素可见（按 `R` 归位），不会拖丢。
- **切工具 / 换颜色不发包**：`0x01`/`0x02` 落笔包自带工具与颜色字节，所以画笔/橡皮/油漆桶切换与调色
  都是纯客户端状态；需要让插件自己的手持工具手势用上时，在控制台菜单点「同步工具/颜色」（`0x09`/`0x0A`）。
- **笔刷大小**：画笔与橡皮共用（配置项 `brushSize`，1~16 个逻辑格）。落笔时以点中的逻辑格为中心涂
  `size × size` 个格子，同一笔里同一格只发一次，悬停预览也会跟着变大。
- **调色板历史颜色**：只记录「真的画上去过」的颜色（选中/预览不算），最新在前、最多 18 个；
  区块右上角「清空」可清空，右键单个色格只删一个。
- **画板有返回按钮**：顶栏返回箭头 + 右面板「返回控制台菜单」，`Esc` 仍是直接关掉画板。
- **拦截插件原生菜单**：插件对「手持画布右键」和「右键展示框」都会弹它自己的容器菜单，
  客户端挂在 `ScreenEvents.AFTER_INIT` 上在**第一次渲染之前**就换成客户端菜单（不会闪一下）。
- **保护模式保护**：已锁定的画布本地直接拒绝编辑（作者本人除外）。
- **自动同步**：打开画板、以及元数据提交之后，都会自动发一次 `0x0C`；收到同步时若「刚打开过客户端界面」，
  不会再自动弹画板去顶掉当前界面。
- **操作反馈**：状态栏 +（无界面时）聊天栏。
- **换服/断线自动清缓存**：监听 Fabric API 的 `ClientPlayConnectionEvents.DISCONNECT`，
  断开连接时清空画布缓存，不会把上一个服务器的画布带到新连接里。
- **纯矢量 UI**：不依赖任何原版控件贴图，26.2 的 GUI 重构不会影响显示（见 `ASSETS.md`）。

---

## 2. 怎么打开界面

**只有一个键位**：按 `J` 打开「MapDraw 控制台」菜单，其余界面全从菜单里进。用 J 而不是 M，是因为 M 常被小地图/世界地图类模组占用。

控制台菜单里能直接进：

| 左列（功能） | 右列（服务端 / 同步） |
| :--- | :--- |
| 打开画板 | 识别手持地图（读 PDC 里的 canvas_id） |
| 调色板 | 重新同步（`0x0C`） |
| 画布列表 / 输入 ID | 保护 / 解锁（`0x05` / `0x06`） |
| 新建画布（`0x08`） | 服务端菜单（`0x0B` guiType=0） |
| 画布元数据（`0x07`） | 服务端调色板（`0x0B` guiType=1） |
| — | 底部开关：网格 / 透明棋盘格 / 关闭 |

### 鼠标手势（更直观，不用记键位）

| 操作 | 效果 |
| :--- | :--- |
| **手持画布地图 → 右键** | 打开客户端**二级菜单**（控制台），从那里再进画板 / 调色板；不会弹插件的原生菜单 |
| **对着放着画布地图的展示框 → 右键** | 同上，打开客户端二级菜单（配置项 `openBoardOnFrameClick` 可关） |
| **手持插件工具（画笔/橡皮/油漆桶）右键** | 不拦截，交回插件自己的射线绘制手势 |

### 界面内按键

| 按键 | 作用 |
| :--- | :--- |
| `1` / `2` / `3` / `4` | 画笔 / 橡皮擦 / 油漆桶 / 无工具 |
| `H` | 重新从手持/背包地图读画布 ID |
| `Ctrl+Z` / `Ctrl+Y` / `Ctrl+Shift+Z` | 本地撤销 / 重做 |
| `Ctrl+S` | 重新同步画布（`0x0C`） |
| `K` | 锁定保护 / 解除保护 |
| `G` | 像素网格开关 |
| `,` / `.` | 笔刷调小 / 调大（画笔与橡皮共用） |
| `R` | 平移归零 |
| `+` / `-` / 滚轮 | 缩放（以鼠标光标为中心；自动适配 或 1×2×3×4×6×8×12×16×24×32） |
| `Esc` | 关闭当前界面 / 返回上一级 |

唯一键位 `J` 可以在「选项 → 控制 → 按键绑定 → 杂项」里改。

---

## 3. 画布 ID：自动识别（不用手输）

插件的 `CanvasNBTUtil` 把画布信息写进了 Bukkit 的 PersistentDataContainer
（`mapdraw:canvas_id` / `title` / `size` / `protected` …），而 Paper 会把 PDC 序列化进物品的
`minecraft:custom_data` 组件（`{PublicBukkitValues: {...}}`）。因此本模组**能直接从地图物品里读出画布 ID**：

- 打开画板（菜单里的「打开画板」，或画板内按 `H`）时若还没选画布，会自动扫 **主手 → 副手 → 整个背包**，找到就自动同步；
- 画板内按 `H` 或点「读手持地图 (H)」可随时重新识别；
- 画布列表（`L`）里的「从手持地图读取 (PDC)」会读出 ID 并直接切过去；
- 插件 `/mdw create` 新建的画布地图是放进**背包**的（`getInventory().addItem`），所以创建完立刻就能被扫到。

也仍然支持手动输入 / `Ctrl+V` 粘贴 UUID（在画布列表界面里）。

---

## 4. 配置文件

首次启动后生成 `config/mapdrawclient.json`：

```json
{
  "autoOpenBoardOnSync": true,
  "batchFlushPoints": 64,
  "resyncDelayTicks": 3,
  "requestOnOpen": true,
  "debugPacketLog": false,
  "selfTest": false,
  "openBoardOnFrameClick": true,
  "openBoardOnRightClick": true,
  "lightTheme": false,
  "openMenuOnJoin": false,
  "imageHost": "catbox",
  "suppressPluginMenu": true,
  "clearNewCanvas": true,
  "showGrid": true,
  "autoFit": true,
  "zoom": 1,
  "tool": 0,
  "color": 114,
  "lastCanvasId": "",
  "newCanvasName": "我的画作",
  "newCanvasSize": 128,
  "showCheckerboard": true
}
```

- `batchFlushPoints`：单个 `0x02` 包最多几个点（默认 64，越大越省流量、越大越迟钝）。
- `resyncDelayTicks`：绘制失败/元数据变更之后隔多少 tick 发一次 `0x0C`。
- `autoOpenBoardOnSync`：收到画布同步且当前没开界面、也没刚开过界面时，自动打开画板。
- `clearNewCanvas`：新建画布后自动清成透明（默认开；想要插件的白底就关掉）。
- `autoFit`：缩放自动适配整张 128×128 画布（默认开，小尺寸画布也能铺满画板）。
- `suppressPluginMenu`：把插件自己弹的原生菜单换成客户端菜单（默认开）。
- `lightTheme`：浅色主题（菜单底部可切换）。
- `imageHost`：本地图片上传用的免费图床（`catbox` / `uguu` / `0x0`）。
- `debugPacketLog`：把收发到的包打进日志（排障用）。

---

## 5. 协议实现说明（重要）

服务端用 `ByteStreams.newDataInput(message)` 解析负载，也就是 **`DataInputStream` 语义**：

- `readUTF()` = **2 字节长度前缀 + 修改版 UTF-8**
- 整数 = 大端

而 Minecraft 的 `FriendlyByteBuf#writeUtf` 用的是 **VarInt 长度前缀**，两者**不兼容**。
因此本模组不使用 `FriendlyByteBuf` 拼包，而是自己用 `java.io.DataOutputStream` 逐字段写出，
再作为裸字节负载放进唯一的一个 payload 类型里（通道名就是 `mapdraw:main`，`PacketID` 是负载的第一个字节）。
这样客户端发出去的字节和 Paper 插件的 `sendPluginMessage(plugin, "mapdraw:main", bytes)` 完全一致。

接收方向 `0x80` / `0x81` 同理，用 `DataInputStream` 按协议顺序读取；
`0x81` 的像素数组按 `int pixelLen` 预读，并对 `pixelLen ≠ 16384` 做了防御性处理。

---

## 6. 构建

```bash
./gradlew build
```

输出：`build/libs/mapdrawclient-1.0.0.jar`（`gradle.properties` 里 `version` 决定）。

依赖：Minecraft 26.2、Fabric Loader ≥ 0.19.5、Fabric API 0.161.0+26.2、Java 25。

**构建状态**：已用 Gradle 9.7.1 + Loom 1.18.2 + JDK 25 **真实构建通过**，
产物 91 KB（含全部 class 与 `assets/mapdrawclient` 资源）。26.2 的 API 变化与逐条 `javap` 验证记录见
`docs/26.2-API-NOTES.md`。

> 本机的 `gradle/wrapper/gradle-wrapper.properties` 把 Gradle 发行包指向了腾讯镜像
> （`services.gradle.org` 直连容易超时）。换机器构建时改回官方地址即可。

---

## 7. 排障

| 现象 | 原因 / 处理 |
| :--- | :--- |
| 画板顶部显示「通道不可用」 | 服务端没装 MapDraw 插件，或该连接没有声明 `mapdraw:main` 通道 |
| 点画布没反应 | 还没选画布（按 `L`），或画布处于保护状态 |
| 画上去又变回去了 | 服务端拒绝了这次绘制（权限/保护），客户端已自动重新同步；看底栏的红色提示 |
| 新建的画布是一片白 | `clearNewCanvas` 被关掉了，或创建后没在背包里找到新画布地图（日志会写明），可在画板里按 `H` |
| 右键地图闪一下插件菜单 | `suppressPluginMenu` 被关掉了；开启后客户端在原生菜单第一次渲染前就替换成自己的菜单 |
| 方块/文字错位 | 窗口太窄或 GUI 缩放过大导致布局被压缩；界面本身做了自适应，但过小仍会拥挤 |
| 想看原始包 | 打开配置里的 `debugPacketLog`，日志前缀 `[MapDrawClient]` |

---

## 8. 目录结构

```
src/main/java/top/colorgarden/mapdrawclient/
├── MapDrawClient.java              客户端入口
├── MapDrawConfig.java              config/mapdrawclient.json
├── net/
│   ├── MapDrawProtocol.java        通道名 / PacketID / 枚举 / 常量 / 逻辑格换算
│   ├── MapDrawPayload.java         唯一 payload（裸字节，与插件消息互通）
│   ├── MapDrawClientNetworking.java 0x01~0x0C 发送 + 0x80/0x81 解析
│   └── ImageHostUploader.java      本地图片 → 免费图床 URL
├── canvas/
│   ├── MapPalette.java             244 色地图调色板 / 最近色 / 颜色解析
│   ├── CanvasData.java             单张画布（元数据 + 16384 像素 + 逻辑格换算）
│   ├── CanvasStore.java            缓存 / 当前画布 / 回执 / 延迟重同步 / 新建清空 / 菜单拦截
│   ├── EditHistory.java            本地撤销重做（按操作记录像素差异）
│   └── HeldMapProbe.java           从物品 PDC 读 canvas_id（手持地图识别）
├── input/
│   ├── MapDrawKeys.java            键位（只有一个 J）
│   └── BoardOpenHandler.java       右键手势：画布地图/展示框 → 客户端二级菜单
└── ui/
    ├── UiKit.java                  面板/按钮/滑条/色块/气泡绘制
    ├── UiIcon.java                 矢量图标
    ├── MapDrawScreen.java          自绘控件基类（按钮/输入框/快捷键）
    ├── MainMenuScreen.java         控制台菜单（J 打开）
    ├── BoardScreen.java            画板主界面
    ├── PaletteScreen.java          调色板
    ├── CreateCanvasScreen.java     新建画布 (0x08)
    ├── MetaScreen.java             元数据 (0x07)
    ├── CanvasListScreen.java       画布列表 / ID 输入
    ├── UploadScreen.java           上传图片（本地文件 → 图床 → /mdw upload）
    ├── PickerScreen.java           屏幕取色器（取屏幕任意像素 → 最近地图像素）
    └── ClipboardHelper.java        Ctrl+V 粘贴
```
