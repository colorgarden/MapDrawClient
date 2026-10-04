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
| 撤销 / 重做 | 按钮 + `Ctrl+Z` / `Ctrl+Y`；发送后自动延迟重同步真实像素 | `0x03` / `0x04` |
| 保护 / 解除保护 | 一键锁定；本地会立刻进入不可编辑状态 | `0x05` / `0x06` |
| 元数据编辑 | 标题、描述、可用尺寸(16/32/64/128)、防拷贝开关 | `0x07` |
| 新建画布 | 名称 + 尺寸，服务端扣费与权限校验照旧 | `0x08` |
| 工具切换 | 画笔 / 橡皮擦 / 油漆桶 / 无工具，同时同步给服务端 | `0x09` |
| 颜色设置 | 16 色快捷格、画布用色统计、RGB 滑块、`#RRGGBB` / 英文色名 / `&a` 代码解析 | `0x0A` |
| 服务器界面 | 直接请求服务端打开它自己的菜单 / 调色板箱子 | `0x0B` |
| 画布同步 | 请求完整元数据 + 16384 像素并本地缓存渲染 | `0x0C` |
| 回执显示 | `0x80` 的成功/失败提示显示在状态栏，无界面时也会进聊天栏 | `0x80` |
| 画布数据 | `0x81` 全量同步，落库为客户端画布缓存（含 mapId 反查） | `0x81` |

其它细节：

- **乐观落笔**：本地先画上去保证手感；服务端返回失败时自动重新拉取真实像素纠正。
- **超出可用尺寸保护**：`size` 之外的像素区域会被压暗且禁止编辑。
- **保护模式保护**：已锁定的画布本地直接拒绝编辑（作者本人除外）。
- **自动同步**：打开画板、以及撤销/重做/元数据提交之后，都会自动发一次 `0x0C`。
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
| **手持画布地图 → 右键** | 直接打开这张画布的画板 |
| **手持画布地图 → 蹲下右键** | 不拦截，交回插件自己的 `/mdw menu` 菜单 |
| **对着放着画布地图的展示框 → 蹲下空手右键** | 打开该画布的画板（配置项 `openBoardOnFrameClick` 可关） |

### 界面内按键

| 按键 | 作用 |
| :--- | :--- |
| `1` / `2` / `3` / `4` | 画笔 / 橡皮擦 / 油漆桶 / 无工具 |
| `H` | 重新从手持/背包地图读画布 ID |
| `Ctrl+Z` / `Ctrl+Y` | 撤销 / 重做 |
| `Ctrl+S` | 重新同步画布（`0x0C`） |
| `K` | 锁定保护 / 解除保护 |
| `G` | 像素网格开关 |
| `R` | 平移归零 |
| `+` / `-` / 滚轮 | 缩放（1×2×3×4×6×8） |
| `Esc` | 关闭当前界面 / 返回上一级 |

唯一键位 `J` 可以在「选项 → 控制 → 按键绑定 → 杂项」里改。

---

## 3. 画布 ID：自动识别（不用手输）

插件的 `CanvasNBTUtil` 把画布信息写进了 Bukkit 的 PersistentDataContainer
（`mapdraw:canvas_id` / `title` / `size` / `protected` …），而 Paper 会把 PDC 序列化进物品的
`minecraft:custom_data` 组件（`{PublicBukkitValues: {...}}`）。因此本模组**能直接从地图物品里读出画布 ID**：

- 打开画板（`M`）时若还没选画布，会自动扫 **主手 → 副手 → 整个背包**，找到就自动同步；
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
  "showGrid": true,
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
- `resyncDelayTicks`：撤销/重做/元数据之后隔多少 tick 发一次 `0x0C`。
- `autoOpenBoardOnSync`：收到画布同步且当前没开界面时，自动打开画板。
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
| 点画布没反应 | 还没选画布（按 `L`），或画布处于保护状态，或坐标超出 `size` 可用范围 |
| 画上去又变回去了 | 服务端拒绝了这次绘制（权限/保护），客户端已自动重新同步；看底栏的红色提示 |
| 方块/文字错位 | 窗口太窄或 GUI 缩放过大导致布局被压缩；界面本身做了自适应，但过小仍会拥挤 |
| 想看原始包 | 打开配置里的 `debugPacketLog`，日志前缀 `[MapDrawClient]` |

---

## 8. 目录结构

```
src/main/java/top/colorgarden/mapdrawclient/
├── MapDrawClient.java              客户端入口
├── MapDrawConfig.java              config/mapdrawclient.json
├── net/
│   ├── MapDrawProtocol.java        通道名 / PacketID / 枚举 / 常量
│   ├── MapDrawPayload.java         唯一 payload（裸字节，与插件消息互通）
│   └── MapDrawClientNetworking.java 0x01~0x0C 发送 + 0x80/0x81 解析
├── canvas/
│   ├── MapPalette.java             244 色地图调色板 / 最近色 / 颜色解析
│   ├── CanvasData.java             单张画布（元数据 + 16384 像素）
│   └── CanvasStore.java            缓存 / 当前画布 / 回执 / 延迟重同步
├── input/MapDrawKeys.java          键位
└── ui/
    ├── UiKit.java                  面板/按钮/滑条/色块/气泡绘制
    ├── UiIcon.java                 矢量图标
    ├── MapDrawScreen.java          自绘控件基类（按钮/输入框/快捷键）
    ├── BoardScreen.java            画板主界面
    ├── PaletteScreen.java          调色板
    ├── CreateCanvasScreen.java     新建画布 (0x08)
    ├── MetaScreen.java             元数据 (0x07)
    ├── CanvasListScreen.java       画布列表 / ID 输入
    └── ClipboardHelper.java        Ctrl+V 粘贴
```
