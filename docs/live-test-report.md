# 真实联调验证记录（Paper 26.2 + MapDraw 插件 + 本模组）

本次是**真机、真服务端、真插件**的端到端验证，不是模拟。

## 一、环境

| 组件 | 版本 / 路径 |
| :--- | :--- |
| 服务端核心 | Paper **26.2 build 129**（`D:\Game\testserver\paper-26.2-129.jar`，sha256 `b1d8f6bf…b083` 已校验） |
| 服务端插件 | **mapdraw 1.0-SNAPSHOT**（`api-version: '26.2'`，main `sashwind.mc.plugin.mapdraw.Mapdraw`） |
| 客户端 | 本项目 `./gradlew runClient`，Minecraft 26.2 + Fabric Loader 0.19.5 + Fabric API 0.161.0+26.2 |
| 客户端配置 | `run/config/mapdrawclient.json`（`selfTest: true`、`debugPacketLog: true`） |
| 启动参数 | `--quickPlayMultiplayer 127.0.0.1:25565`（写在 `build.gradle.kts` 的 loom runs 里） |

服务端启动日志确认插件就绪：

```
[mapdraw] Enabling mapdraw v1.0-SNAPSHOT
[mapdraw] 未检测到 Vault 插件，经济收费功能将自动禁用。
[mapdraw] 已注册客户端 Mod 原生网络数据包通信通道: mapdraw:main
[mapdraw] MapDraw 地图绘制插件已成功加载！
Done (14.672s)!
```

## 二、客户端自检结果（`run/logs/latest.log`）

自检状态机：通道 → 创建画布 → 读 PDC → 拉像素 → 落笔 → 回读验证。

```
[MapDrawClient] 连接检查: mapdraw:main 通道可用 = true
[SelfTest] ===== 阶段 0 =====
[SelfTest] mapdraw:main 通道可用 = true
[SelfTest] 背包里没有画布地图，发送 0x08 CREATE_CANVAS
[MapDrawClient] -> CREATE_CANVAS(0x08) (27 bytes)
[MapDrawClient] <- 81 bytes: 80 08 01 00 4C C2 A7 ...
[SelfTest] 收到 0x80 回执: original=CREATE_CANVAS(0x08) success=true
           message=§a成功创建画布地图: §eMapDrawClient 自检 §a(大小: 128x128)!
[SelfTest] ===== 阶段 1 =====
[SelfTest] 创建成功，从物品 PDC 读到 canvas_id=1e899645-f14d-4844-a72b-06a26cfc0987
           title=MapDrawClient 自检
[MapDrawClient] -> REQUEST_CANVAS(0x0C) (39 bytes)
[MapDrawClient] <- 16504 bytes: 81 00 24 31 65 38 39 39 36 34 35 2D ... (16384 像素)
[SelfTest] 收到 0x81 CANVAS_DATA_SYNC: id=1e899645-… size=128 保护=false 已落笔像素=16384
[SelfTest] ===== 阶段 2 =====
[MapDrawClient] -> DRAW_PIXEL(0x01) (45 bytes)
[MapDrawClient] <- 5 bytes: 80 01 01 00 00
[SelfTest] 收到 0x80 回执: original=DRAW_PIXEL(0x01) success=true
[SelfTest] ===== 阶段 3 =====（重新 0x0C 拉取）
[SelfTest] 回读 (10,10) 像素 = 114 (PASS 落笔已生效，整链路 OK)
```

统计（整局）：`0x01` ×1、`0x02` ×8、`0x0C` ×3、收到 `0x81` ×3、`0x80` 成功 20 / 失败 **0**。

## 三、反编译插件核对协议（javap）

把插件解包后用 `javap -c` 逐方法核对，结论：**本模组 12 个 C2S 包与 2 个 S2C 包的字段顺序、类型、编码全部与插件一致**。

| PacketID | 插件读取顺序（bytecode 实测） | 本模组写出 |
| :--- | :--- | :--- |
| `0x01` | UTF, short, short, byte, byte | 一致 |
| `0x02` | UTF, byte, byte, short(count), 循环 short,short | 一致 |
| `0x03/04/05/06` | UTF | 一致 |
| `0x07` | UTF, byte, UTF | 一致 |
| `0x08` | UTF, int | 一致 |
| `0x09` | byte | 一致 |
| `0x0A` | int, int, int | 一致 |
| `0x0B` | byte, UTF | 一致 |
| `0x0C` | UTF | 一致 |
| `0x80`（收） | byte(0x80), byte(origId), boolean, UTF | 按此解析 |
| `0x81`（收） | byte(0x81), UTF(id), int(mapId), UTF(name), UTF(title), UTF(desc), int(size), boolean(protected), boolean(noCopy), UTF(creator?:"") , boolean(animated), int(pixelLen), byte[] | 按此解析 |

插件 `PacketProtocol` 常量（`javap -constants`）：

```java
public static final String  DEFAULT_CHANNEL = "mapdraw:main";
public static final byte C2S_DRAW_PIXEL=1, DRAW_BATCH=2, UNDO=3, REDO=4, PROTECT=5, DEPROTECT=6,
                          SET_META=7, CREATE_CANVAS=8, SET_TOOL=9, SET_COLOR=10, OPEN_GUI=11, REQUEST_CANVAS=12;
public static final byte S2C_RESPONSE=-128(0x80), S2C_SYNC_CANVAS=-127(0x81), S2C_PIXEL_UPDATE=-126(0x82);
public static final byte FIELD_TITLE=0, FIELD_DESCRIPTION=1, FIELD_SIZE=2, FIELD_NO_COPY=3;
```

## 四、比 README 文档多出来的关键情报（已据此改代码）

### 1. 插件用 PDC 存画布 ID —— 客户端现在能自动识别画布

插件 `CanvasNBTUtil` 用 `new NamespacedKey(plugin, …)` 写 PersistentDataContainer：

```
mapdraw:is_canvas / mapdraw:canvas_id / mapdraw:title / mapdraw:description
mapdraw:size / mapdraw:protected / mapdraw:no_copy / mapdraw:creator
```

Paper 会把 PDC 序列化进物品的 `minecraft:custom_data` 组件，结构为
`{PublicBukkitValues: {"mapdraw:canvas_id": "…"}}`。

因此本模组新增 `HeldMapProbe`：直接读主手/副手/整个背包里地图物品的该组件，
**不需要任何额外通道就能拿到 canvasId**。实测日志见上（阶段 1 一行）。

### 2. `0x82 S2C_PIXEL_UPDATE` 是"定义了但没用"

全量反汇编 429 个类，`bipush -126` **零命中** —— 插件 1.0-SNAPSHOT 从未发送它。
本模组只识别 + 记日志，不猜字段（协议未定就实现反而危险）。

### 3. `0x08` 创建后地图进的是背包

`MapDrawAPIImpl#createCanvas` → `player.getInventory().addItem(...)`；
回执里也**不含**画布 ID，所以"创建完自动打开新画布"必须靠读背包物品的 PDC 实现。

### 4. 新建画布默认是白色底

同步回来的 `已落笔像素=16384`（全部非 0），说明插件初始化时把整张画布填成了白色，
而不是 README 里写的 `default_bg_color: 0`（透明）。界面显示与截图像一致。

## 五、顺带修掉的真实 bug

- **0x0C 失败会死循环**：原来任何操作失败都会 `markStale(currentId)` → 再发 `0x0C` → 再失败…
  现在只有 `0x01~0x07` 失败才重拉像素，`0x0C` 自身失败只提示。
- **底栏文字重叠**：提示文字与右侧状态消息会叠在一起，现在先算状态宽度再裁剪提示。

## 六、界面实拍

`build/shots/client2.png`（DPI-aware 截的窗口客户区）可以看到：
顶部标题栏 + 右侧 7 个图标按钮、左侧画布视口（用户已用红色笔刷画出图形）、
右面板（工具/颜色/调色板/快捷色/操作/读手持地图(H)）与底栏快捷键提示 —— 全部正常渲染。
