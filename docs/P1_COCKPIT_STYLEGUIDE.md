# PINTA // P1 COCKPIT — UI STYLE LOCK v1.4

**锁定日期**：2026-10-27  
**基准设备**：Samsung SM-S9210，1080×2340，360×780 dp，480 dpi，竖屏，三键导航  
**实现**：Jetpack Compose / Material3 / dark only  
**状态**：座舱页最终基线；调校、油门、监控、设置仍是旧布局，仅沿用绿色 token

> 这份文档是后续 UI 的“母版”。新页面先对齐这里的颜色、字号、行高、边框、密度和状态语义，再谈新布局。  
> 不要用 Material 默认卡片的视觉语言覆盖它。

> **基线截图**：真机截图属于本地中间产物，不入库（`docs/images/` 已在 `.gitignore`）。
> 需要对照时自行在 360×780 dp 真机上截一张；旧图仍可从历史提交取回：
> `git show 5d23438:docs/images/p1-cockpit-dashboard.png > baseline.png`

---

## 1. 设计意图

- **不是“App 仪表盘”，是车载 CRT 服务终端**：固定外壳、状态条、面板框、分段条、终端式状态字。
- **密度优先**：一屏尽量多给可读数据，不靠滚动和留白撑版面。
- **小字号层级**：hero 只有 26sp；主要靠亮度、字重、对齐、位置分主次。
- **游戏感放在状态变化里**：链路、读取、写入、故障；静止时不闪、不抖、不做无意义 glitch。
- **专业感来自秩序**：1px 框、固定列、等宽数字、固定小数位、状态双编码。

---

## 2. 颜色 Token

### 2.1 表面与文字

| Token | Hex | 用途 |
|---|---|---|
| `Ink` | `#030805` | 全局背景，近黑带绿 |
| `SurfaceLow` | `#07110B` | 面板底（实际使用 28% alpha） |
| `SurfaceContainer` | `#0A150E` | 略高的面 |
| `SurfaceHigh` | `#102117` | 分段条轨道、局部高亮 |
| `SurfaceHighest` | `#162C1D` | 更高一层面 |
| `TextPrimary` | `#CFFFE0` | 主文字、主数值 |
| `TextDim` | `#7FAE8D` | 次级文字、单位、说明 |
| `TextFaint` | `#5E8C70` | 元数据、表头、非关键标签 |

### 2.2 磷光与告警

| Token | Hex | 用途 |
|---|---|---|
| `Phosphor` | `#4DFF7C` | 关键值、活动点、选中态、主强调 |
| `PhosphorHot` | `#B6FFC9` | 更亮的反白文字/高亮，少量 |
| `PhosphorDim` | `#2E8F55` | 次级曲线、非焦点数据 |
| `Amber` | `#FFB020` | 变更、注意、待确认、Flash 保存 |
| `Danger` | `#FF4D4D` | 故障、错误、破坏性操作 |

### 2.3 线条

| Token | Value | 用途 |
|---|---|---|
| `Hairline` | `#1F58FF8C` | 1px 面板框、分隔线、图表网格 |
| `HairlineStrong` | `#3858FF8C` | 需要更强一点的边界 |

### 2.4 兼容别名

其它旧页面仍引用以下名字，不要在新代码里继续扩散：

- `Orange = Phosphor`
- `OrangeSoft = PhosphorHot`
- `Mint = #8CFFB0`（旧页面正向色，新座舱页不使用）

### 2.5 用色规则

- 只有绿、琥珀、红三种语义色；**不要蓝、青、紫、洋红**。
- 绿色分 5 级亮度：100% 关键值 / 72% 正文 / 48% 标签 / 24% 网格 / 12% 选中底。
- 琥珀只给“变更/注意/待确认/Flash”，不做普通主按钮。
- 红色只给“故障/错误/破坏性”，不做装饰。
- 主按钮/选中态用**反白**（Phosphor 底 + 黑字），不用琥珀填充。
- 颜色不能单独承载状态：必须配文字、形状或图标。

---

## 3. 字体与字号

### 3.1 字体家族

- `Mono` = `FontFamily.Monospace`：所有数字、英文标签、状态字、日志。
- `Sans` = `FontFamily.SansSerif`：中文正文、标题。
- **当前没有打包品牌字体**。如果后续换 IBM Plex Mono，必须重新真机检查行高、字宽、数字列宽；不要只替换字体文件。

### 3.2 全局 Typography（`Theme.kt`）

| Token | 字体 | 字号 | 字重 | 字距 | 用途 |
|---|---|---:|---:|---:|---|
| `displaySmall` | Mono | 26sp | SemiBold | -0.4sp | 车速 hero |
| `headlineMedium` | Mono | 18sp | Medium | -0.2sp | 大号关键值 |
| `titleLarge` | Sans | 15sp | SemiBold | 0 | 页面标题 |
| `titleMedium` | Sans | 13sp | Medium | 0 | 小标题 |
| `bodyMedium` | Sans | 12sp | Regular | 0 | 中文正文 |
| `bodySmall` | Sans | 10sp | Regular | 0 | 辅助正文 |
| `labelMedium` | Mono | 10sp | Medium | 0.6sp | 技术标签 |
| `labelSmall` | Mono | 9sp | Medium | 1.0sp | 小标签 |

数字启用 `tnum`（tabular figures），避免刷新时布局抖动。

### 3.3 座舱页实际字号（必须按此对齐）

| 位置 | 字号 | 字重 | 颜色 | 备注 |
|---|---:|---|---|---|
| 顶部 `PINTA` | 10sp | Bold | Phosphor | 字距 1.2sp |
| `// FIELD CONSOLE` | 8sp | Normal | TextFaint | 字距 0.4sp |
| 链路 token | 9sp | Medium | 状态色 | `[NO LINK]` / `[LINK 2M]` |
| 页头右侧上下文 | 8sp | Normal | TextFaint | `PHY/MTU/CAN/ERR` |
| 页头 index / 面板 `[RUN]` | 8sp | Normal | TextFaint/TextDim | |
| 页头 `COCKPIT` | 10sp | Medium | Phosphor | 字距 1.4sp |
| 面板 index / title | 8sp | Normal/Medium | TextFaint/Phosphor | title 字距 1.0sp |
| 面板右侧状态 | 8sp | Normal | TextDim/TextFaint | |
| 车速 | 26sp | SemiBold | TextPrimary | `km/h` 为 9sp |
| THR/STR 条标签/值 | 8sp | Normal/Medium | TextFaint/TextDim | |
| IO key glyph | 11sp | SemiBold | 状态色/黑 | `1/2/3/R/P` |
| IO key label | 6sp | Normal | TextFaint/黑 | `REV/FIX/L/R/BRK` |
| IO analog label/value | 8sp / 10sp | Normal | TextFaint/TextPrimary | `TH/STR` |
| IO sense label/value | 6.5sp / 8sp | Normal | TextFaint/TextPrimary | `NTC/2.0REF/V_IU/V_ACC/V_12` |
| Motor cell index | 7sp | Normal | TextFaint | `03` |
| Motor cell name | 10sp | Medium | Phosphor | `FL / FR / RL / RR` |
| Motor cell CTRL/MOT/fault | 7sp | Normal | TextDim / Danger | `CTRL 40.4°  MOT 45.4° [OK]` |
| Motor legend | 6sp | Normal | TextDim | `IQ 92.7 / IB 11.6 / ω 1902` |
| EVENT | 8/9sp | Normal | TextFaint/Phosphor | |
| 底部导航图标/文字 | 17dp / 9sp | — | Phosphor/TextFaint | |

### 3.4 关键：`lineHeight = 1.em`

座舱页根节点必须包：

```kotlin
CompositionLocalProvider(
    LocalTextStyle provides TextStyle(lineHeight = 1.em)
) {
    // dashboard
}
```

原因：Material3 `LocalTextStyle` 默认行高约 24sp；只改 `fontSize = 7sp` 时，行框仍然是 24sp，会把 `IO` 小标签、电机格 header/legend 裁掉。  
**所有后续高密度页面都必须显式收紧 lineHeight，不能只改字号。**

---

## 4. 布局与密度

### 4.1 屏幕骨架

| 区域 | 高度/尺寸 |
|---|---:|
| 状态栏 | 系统 |
| Top Rail | 32dp |
| Page Header | 20dp |
| 页面内容外边距 | 左右 6dp，上下 4dp |
| 模块间距 | 4dp |
| 底部导航 | 52dp + navigationBarsPadding |
| 底部三键导航 | 系统，约 48dp |

顶栏现在只有 **32dp + 20dp 两段 + 2 条 1px 分隔线**，总高约 52dp。  
`PHY / MTU / CAN / ERR` 已从原来的独立 Context Rail 移入 Page Header 右侧，替换旧的 `DEMO [RUN]`；页面不再有第三段上下文条。不要恢复旧的 54dp 大标题栏或额外上下文条。

**顶栏由 Shell 持有，固定在所有 tab 之上，不随页面滚动。**  
第一行 `PINTA // FIELD CONSOLE` 永远不变；第二行按 tab 切换：

| tab | 第二行 | 右侧 context |
|---|---|---|
| 座舱 | `00 // COCKPIT` | `PHY/MTU/CAN` |
| 调校 | `10 // TUNE` | `PROFILE · 111 VAR / 44 REF` |
| 监控 | `20 // SCOPE` | `8 CH · ACQ_C / 1–255 ms` |
| 设置 | `30 // SYSTEM` | `IUPro v1.260406.3 · CONFIG` |

调校二级页继续用 10.x，并在左侧显示 `←` 返回：

| 页面 | 第二行 |
|---|---|
| 整车布局 | `10.1 // LAYOUT` |
| 油门曲线 | `10.2 // THROTTLE` |
| 转向与差速 | `10.3 // STEERING` |
| 电池与保护 | `10.4 // BATTERY` |
| 控制器与固件 | `10.5 // NODES` |

所有页面的 `[SIM]` 都在 Page Header 右侧末尾。

### 4.2 模块高度（360×780，非 compact）

| 模块 | 高度 |
|---|---:|
| `01 // DRIVE` | 94dp |
| `02 // IO` | 146dp（compact 140dp） |
| `03–06 // MOTOR MATRIX` | weight(1f)，约 331dp；scroll 时固定 300dp |
| `EVENT` | 18dp |
| `FAULT`（有故障时） | 22dp |

`03 // FL / 04 // FR / 05 // RL / 06 // RR` 是四个等大的 2×2 电机格；`04 POWER`、`05 THERMAL`、`06 LINK`、`07 RESPONSE` 已全部移除。  
compact 下 Drive 94 + IO 140 + grid 331 + Event 18 + 3×4dp 间距 + 8dp padding ≈ 603dp，正好一屏。

### 4.3 紧凑/滚动阈值

```kotlin
val compact = maxHeight < 630.dp
val needScroll = maxHeight < 570.dp || faultCode != 0
```

- `compact = true`：IO 140；矩阵仍占剩余高度。
- `needScroll = true`：内容改用 `verticalScroll`，矩阵固定 300dp，不裁切。
- 360×640 或字体放大时走滚动，不要硬压字号。
- 顶部状态栏/导航栏高度变化由 `maxHeight` 自动适配，不要写死屏幕高度。

### 4.4 面板内部

| 部件 | 规格 |
|---|---|
| 面板边框 | 1px `Hairline` |
| 面板底 | `SurfaceLow` 28% alpha |
| 面板头 | 20dp，左右 5dp |
| 标题分隔线 | 1px `Hairline` |
| 面板 body padding | 左右 5dp，上下 4dp |
| IO keys 行 | 高度按 weight 1.25 填充，9 等分，2dp 间隙 |
| IO analog / sense 行 | 高度按 weight 1 填充，等分，4dp 间隙 |
| IO 分组 | 无 KEYS/ANALOG/SENSE 小标题，仅用 4dp 间距分组 |
| Motor cell header | 约 28–30dp（标题 + CTRL/MOT/fault 两行） |
| Motor cell legend | 约 10dp（IQ / IB / ω 三项） |
| 圆角 | 0 |
| 阴影/elevation | 无 |

### 4.5 触控

- 底部导航整格 52dp 高，可点。
- 顶部链路 token 视觉较小，但交互区应 ≥44dp；新页面统一 ≥48dp。
- 不依赖 hover；所有按压态用反白/亮度变化，不用 Material ripple。

---

## 5. 组件规格

### 5.1 `DashPanel`

```text
┌ 02 // IO ───────────────────────── IU ┐
│  content                              │
└───────────────────────────────────────┘
```

- 外框 1px `Hairline`；无填充卡片、无圆角、无阴影。
- Header 20dp；index/title 8sp；右侧状态 8sp。
- 标题行下 1px 分隔线。
- Body 使用 `Column(fillMaxSize())`，子项可用 weight 平分。
- 面板必须 `fillMaxWidth()` + `fillMaxHeight()`（在固定高度/weight 内）。

### 5.2 `SegmentBar`

- 20 段，段间距 1dp，条高 6dp。
- 已填充：`Phosphor`；未填充：`SurfaceHigh`。
- 轨道用段块，不用连续圆角条。
- 转向用 `CenterBar`：20 段，以中心为零点，向左右填充。
- 过温时输出条变 `Amber`，但不要整行变红。

### 5.3 `TableHead` / 数据表

- 表头 7sp，`TextFaint`，全大写，右对齐数值列。
- 数据行 label 9sp `TextDim`；value 9–10sp `TextPrimary` 或语义色。
- 列宽固定，数字右对齐；不要用空格对齐。
- `IO` 不显示 `KEYS/ANALOG/SENSE` 小标题；三组直接用 4dp 间距区分。
- `IO` keys：9 等分格，2dp 间隙，高度按 weight 1.25 填满；active 统一用 `Phosphor` 底 + 黑字，inactive 只留 1px 框。
- demo 状态：档位 1/2/3 每 2s 轮换；`R / FIX / P / L / R / BRK` 每 1.5s 依次点亮，保证 9 个键都会亮。
- `IO` readout：每个 box 1px 框；label/value 各占 1:1 半区，**分别在各自半区水平居中**；中间 1px 竖线；ANALOG 2 等分，SENSE 5 等分；高度按 weight 1 填满。

### 5.4 `MotorGrid` / `MotorCell`

- `03 // FL` / `04 // FR` / `05 // RL` / `06 // RR` 四个等大格，2×2，4dp 间距，填满 03–07 区域。
- 每格顶部居中：`03 // FL` 标题行 + `CTRL 40.4°  MOT 45.4°  [OK]` 状态行。
- 故障为 0 显示绿色 `[OK]`；非 0 显示红色 `[E21]`。
- 中间为 3 条同图叠加曲线：
  - `Iq_fb` → `Phosphor` `#4DFF7C`
  - `Ibus_fb` → `Amber` `#FFB020`
  - `omega_fb` → `TextPrimary` `#CFFFE0`
- 三条曲线分别按满量程归一化：`Iq/135A`、`Ibus/40A`、`omega/2000rpm`；线宽 1.2dp，无面积填充。
- 底部 legend 显示实时值：`IQ 92.7 / IB 11.6 / ω 1902`。
- 不再有独立 `04 POWER` / `05 THERMAL` / `06 LINK` / `07 RESPONSE` 面板。

### 5.5 `EVENT` / 故障

- EVENT 行 18dp；左 `EVENT` 8sp，中状态 9sp，右侧链路/CAN 8sp。
- 故障时在 EVENT 上方插入 22dp 红色 banner：`! FAULT 0xNN / CHECK DRIVE`。
- 故障 banner 使用 `Danger` 12% 底 + 55% 边框；不要整屏红闪。

### 5.6 底部导航

- 高 52dp；顶部 1px `Hairline`。
- 每项：2dp 顶部选中线 + 3dp + 17dp 线性图标 + 2dp + 9sp 中文标签。
- 标签固定为 `座舱 / 调校 / 监控 / 设置`。
- `座舱` 图标是三辐方向盘的自定义 24×24 outline（stroke 1.8），渲染仍为 17dp；不要放大。
- 选中：`Phosphor` 图标/文字/顶线；未选中：`TextFaint`。
- 不要用 Material `NavigationBar` 的 pill/圆角/ripple。

### 5.7 `CrtBackdrop`

- 底色 `Ink`。
- 暗角：径向黑 22%，中心 36% 高，半径 0.72 倍最大边。
- 顶部磷光：`Phosphor` 3.5% → 透明，止于 22% 高。
- 扫描线：黑色 4%，每 3dp 一条，1px。
- 不做曲率、不做 RGB 色散、不做持续闪烁。
- 效果在背景层，不盖文字。当前没有 UI 开关；后续加 `LOW/STD/HIGH` 时保持这个强度为 STD。

### 5.8 Bluetooth console

点击顶部 `[NO LINK]` / `[LINK 2M]` 打开全屏蓝牙控制台。`[NO LINK]` 表示 **BLE 链路**，不是 CAN。

- 全屏覆盖，`Ink` 底，`statusBarsPadding() + navigationBarsPadding()`；不再用 Material BottomSheet/Dialog。
- Header 仍是 32dp + 20dp：`PINTA // FIELD CONSOLE` + `[CLOSE]`；第二行 `L // BLUETOOTH` + `MTU -- / NO LINK`。
- 连接条 34dp：状态、设备名/地址、`SCAN/STOP`、`DISC`。
- 设备条 28dp：扫描结果横向滚动；无名设备不显示。
- `BT RAW` 面板 `weight 1`：
  - Hex 输入 24dp；`SEND` / `CYC` / `STOP` / interval(ms)。
  - RAW log 每行 `HH:mm:ss.SSS TX/RX HEX`，5.5–6sp。
  - 长按任意行复制整行。
- `CCP PROTOCOL` 面板 `weight 1.15`：
  - `LAYOUT`、`BANDWIDTH/STOP`、`CLR`。
  - 带宽条：`OK / FAIL / MS / B/s / VAL`，每项 5.5sp 标签 + 7sp 值。
  - CCP log 每行 `time dir [name] cmd len data chk Δ`，5.5–6sp；长按复制。
  - 底部 `LAT` 条：AVG/MAX + 14dp sparkline。
- 所有框 1px `Hairline`，0 圆角，无阴影；按钮 22dp 高，字号 6.5sp。
- 旧 `BluetoothPage.kt` 的 16dp 圆角、56dp 输入框、300dp RAW、11–14sp 字号在 360dp 宽屏会溢出；新页面全部压到 5.5–9sp 并用 weight 分配高度。

---

## 6. 数据与格式

- 功率：`P = V × I / 1000`，2 位小数，单位 kW。
- 车速：1 位小数，km/h。
- 电压/电流：1 位小数，V / A。
- 温度：1 位小数，℃。
- 转向角：0 位小数，带符号与 `°`。
- `Iq` / `Ibus`：1 位小数，A。
- `ω`：0 位小数，RPM。
- `TH` / `STR`：2 位小数，V。
- `NTC`：1 位小数，℃。
- `2.0REF`：4 位整数 AD，固定宽度，不足补零。
- `V_IU` / `V_ACC` / `V_12`：1 位小数，V。
- 单位字号约为数值的 60–70%，底部对齐。
- 所有数字右对齐；小数位数固定，不因数值变化而抖动。

### 6.1 IO 数据

| UI | 含义 | 显示 |
|---|---|---|
| `1/2/3` | 档位 | 当前档 active，其余 inactive |
| `R` | 倒车 | active = 倒车 |
| `FIX` | 修复/维修 | active = 维修状态 |
| `P` | 驻车 | active = P |
| `L/R` | 左右转向灯 | active = 对应灯 |
| `BRK` | 刹车 | active = 刹车 |
| `TH` | 转把 0–5V | `x.xxV`，2 位小数 |
| `STR` | 转向传感器 0–5V | `x.xxV`，2 位小数 |
| `NTC` | NTC 温度 | `x.x℃`，1 位小数 |
| `2.0REF` | 2.0V 基准 AD | `0000`–`9999`，4 位固定 |
| `V_IU` | IU 电池电压 | `xx.xV`，1 位小数 |
| `V_ACC` | 控制器 ACC 电压 | `xx.xV`，1 位小数 |
| `V_12` | 12V 轨 | `xx.xV`，1 位小数 |

`Telemetry` 字段：`gear / reverse / repair / park / turnLeft / turnRight / brake / throttleVolt / steerVolt / tempC / ref2vAd / vIu / vAcc / v12`。

### 6.2 电机反馈来源

固件 `dyn_fb[n]`，顺序 `FL / FR / RL / RR`：

| UI | 固件字段 | 换算 |
|---|---|---|
| `IQ` 曲线 | `Iq_pu` | `× 135.0 A`，曲线按 135A 归一化 |
| `IB` 曲线 | `Ibus_pu` | `× 40.0 A`，曲线按 40A 归一化 |
| `ω` 曲线 | `omega_pu` | `× 2000 rpm`，曲线按 2000rpm 归一化 |
| `CTRL` 温度 | `tptCtrl` | 1 位小数 ℃ |
| `MOT` 温度 | `tptMotor` | 1 位小数 ℃ |
| 故障 | `errCode` | `0` → `[OK]`；非 0 → `[E%02X]` |

当前 demo 数据由 throttle / ibus / speed 生成，仅用于视觉验证；接 CCU 协议层后替换。  
`Telemetry` 字段：`motorIq`、`motorIbus`、`motorOmega`、`controllerTemps`、`wheelTemps`、`motorFaultCodes`。

---

## 7. 状态语义

| 状态 | 颜色/编码 |
|---|---|
| 正常/测量 | Phosphor / TextPrimary |
| 当前操作/选中 | 反白：Phosphor 底 + 黑字 |
| 变更/注意/待确认 | Amber + 文字/`!` |
| 故障/错误/破坏性 | Danger + 文字/`!` |
| 未连接 | TextFaint + `[NO LINK]` |
| 演示数据 | Amber `[SIM]`，常驻于 Page Header 右侧 |

- 不允许只用颜色深浅表达状态。
- 不允许把 demo 数据画成“已连接/真实车辆”。
- 链路 token 用 `[NO LINK] / [LINK WAIT] / [LINK 2M] / [LINK LOST]`。

---

## 8. Do / Don't

### Do

- 先套 `Theme.kt` 的颜色和字号；页面根节点套 `lineHeight = 1.em`。
- 用 1px 框 + 标题行分组；用对齐制造层级。
- 用固定高度模块，按 360×780 校；小屏走滚动。
- 数字用等宽 + `tnum`；固定小数位；单位缩小。
- 需要密度时优先加表行/微数据，不要加圆角卡片。
- 先保可读，再加 CRT 效果。

### Don't

- 不要 38sp/52sp 的 hero；26sp 是上限附近的基线。
- 不要 Material 默认 `AlertDialog` / `NavigationBar` / 圆角卡片 / 阴影 / ripple。
- 不要给 20dp 以下的小格加趋势线；看不清就删。
- 不要把表头/小字只改 `fontSize` 而不改 `lineHeight`。
- 不要用琥珀做普通按钮；不要用红色做装饰。
- 不要在静止画面加循环 glitch/闪烁/扫描动画。
- 不要用 demo 数据假装真实链路状态。

---

## 9. 复用清单

新页面（例如油门、监控、设置）落地前逐条检查：

- [ ] 颜色只来自 `Theme.kt`，没有新增色相。
- [ ] 页面根节点有 `lineHeight = 1.em`。
- [ ] 顶部仍是 32dp Top Rail + 20dp Page Header 两段式，没有恢复大标题栏或额外 Context Rail。
- [ ] 模块用 1px 框，不是圆角卡片。
- [ ] 数字等宽、固定小数位、单位缩小、右对齐。
- [ ] 面板高度/行高按 360×780 真机校准；360×640 可滚动。
- [ ] `SIM` / `NO LINK` 状态真实，不伪造。
- [ ] 截图与 360×780 dp 真机基线并排比较，密度和字重一致。

---

## 10. 实现入口

| 内容 | 文件 |
|---|---|
| 颜色/全局 Typography | [Theme.kt](../app/src/main/java/com/pinta/phone/ui/theme/Theme.kt) |
| 座舱页、Panel、Bar、Sparkline、Backdrop | [DashScreen.kt](../app/src/main/java/com/pinta/phone/ui/DashScreen.kt) |
| `Telemetry` 字段与 demo 数据 | [Telemetry.kt](../app/src/main/java/com/pinta/phone/core/Telemetry.kt) |
| 底部导航 | [PintaApp.kt](../app/src/main/java/com/pinta/phone/ui/PintaApp.kt) |
| 座舱图标 | [CockpitIcon.kt](../app/src/main/java/com/pinta/phone/ui/icons/CockpitIcon.kt) |
| 系统栏深色图标 | [MainActivity.kt](../app/src/main/java/com/pinta/phone/MainActivity.kt) |
| 设计 brief（方向/取舍） | [CRT_GREEN_UI_BRIEF.md](CRT_GREEN_UI_BRIEF.md) |
| 实现细节/复现记录 | [P1_COCKPIT_IMPLEMENTATION.md](P1_COCKPIT_IMPLEMENTATION.md) |

### 10.1 颜色复制块

```kotlin
val Ink = Color(0xFF030805)
val SurfaceLow = Color(0xFF07110B)
val SurfaceContainer = Color(0xFF0A150E)
val SurfaceHigh = Color(0xFF102117)
val SurfaceHighest = Color(0xFF162C1D)

val Phosphor = Color(0xFF4DFF7C)
val PhosphorHot = Color(0xFFB6FFC9)
val PhosphorDim = Color(0xFF2E8F55)
val Amber = Color(0xFFFFB020)
val Danger = Color(0xFFFF4D4D)

val TextPrimary = Color(0xFFCFFFE0)
val TextDim = Color(0xFF7FAE8D)
val TextFaint = Color(0xFF5E8C70)

val Hairline = Color(0x1F58FF8C)
val HairlineStrong = Color(0x3858FF8C)
```

### 10.2 座舱页骨架

```kotlin
CompositionLocalProvider(
    LocalTextStyle provides TextStyle(lineHeight = 1.em)
) {
    Box(Modifier.fillMaxSize().background(Ink)) {
        CrtBackdrop()
        Column(Modifier.fillMaxSize()) {
            DashHeader(...)
            // fixed-height dashboard modules
        }
    }
}
```

---

## 11. 已知偏差 / 边界

- 只有**座舱页**完成了这版布局；调校、油门、监控、设置仍是旧布局，仅颜色/字体 token 已切换。
- 当前字体是系统 monospace；换 IBM Plex Mono 后要重新校表头宽度和数字列。
- CRT 效果目前固定强度，没有 `LOW/STD/HIGH` 开关。
- 真机数据仍为 demo；四电机矩阵的 Iq/Ibus/ω、CTRL/MOT 温度由 throttle/ibus/speed 生成，不是协议数据。
- 顶部链路 token 当前实现为 `heightIn(28.dp)`，触控偏小；视觉可保持，但后续应扩到 44/48dp。
- 右侧灰色竖条是三星 Edge 面板把手，不是 App 滚动条。
- 固定高度是为 360×780 调的；更大/更小屏幕先看 `compact`/滚动分支，不要直接复制数值。

---

## 12. 版本记录

| 版本 | 日期 | 内容 |
|---|---|---|
| v0.1 | 2026-10-26 | CRT 绿方向、P1 Cockpit 概念、参考拆分 |
| v0.2 | 2026-10-26 | 压缩字号、提升密度、7 模块仪表 |
| **v1.0** | **2026-10-27** | **锁定当前真机 UI：26sp hero、12sp 正文、MOTOR FB、紧凑行高、360×780 基线** |
| **v1.1** | **2026-10-27** | **`02 // TRACTION` → `02 // IO`：9 个按键 + TH/STR 模拟量 + NTC/2.0REF/V_IU/V_ACC/V_12；取消 KEYS/ANALOG/SENSE 小标题，三组按权重填满；按键 active 统一绿色并循环点亮；label/value 各自半区水平居中；顶部上下文并入页头；导航标签改座舱** |
| **v1.2** | **2026-10-27** | **03–07 合并为四电机 2×2 矩阵：每格 Iq/Ibus/ω 三曲线 + CTRL/MOT 温度 + 故障标志；隐藏 04 POWER / 05 THERMAL / 06 LINK / 07 RESPONSE** |
| **v1.3** | **2026-10-28** | **全局固定页头：第一行 PINTA 固定；第二行 00 COCKPIT / 10 TUNE / 20 SCOPE / 30 SYSTEM；调校二级 10.1–10.5 + 返回箭头** |
| **v1.4** | **2026-10-28** | **全屏 Bluetooth Console：SCAN + BT RAW + CCP PROTOCOL + 带宽/延迟；小字号、长按复制** |
