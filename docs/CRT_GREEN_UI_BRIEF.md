# PINTA // P1 COCKPIT

**CRT Green UI — handoff brief for a UI planning agent**  
v0.2 · 2026-10-26 · target 360×780 dp · Android / Jetpack Compose

> 方向：**eDEX-UI 的驾驶舱框架 + btop 的面板语法 + 游戏化状态反馈**。  
> 一句话：手机不是“打开了一个 App”，而是“接上了一台车载 CRT 服务终端”。

> **v0.2 修订（重要）**：压缩字号阶梯、提高每屏信息密度。v0.1 的 52sp hero / 24sp data 作废，以本版为准。  
> 专业感来自**密度、对齐、状态语义**，不是把数字放大。

> **v1.0 已锁定**：实现基线、精确 token 和复用规则见 [P1_COCKPIT_STYLEGUIDE.md](P1_COCKPIT_STYLEGUIDE.md)。  
> 本 brief 保留为方向和取舍文档；实际沿用 UI 时以 style guide 为准。

---

## 0. 怎么用这份 brief

- 这是**规划 brief**，不是最终视觉稿，也不是实现任务。
- 先做 **Dash 一屏 + token 表**，评审通过后再铺其余页面。
- 文中的 **MUST** 是硬约束，**SHOULD** 是默认建议，**MAY** 是可选。
- 不要先写生产代码；先交概念、wireframe、组件状态和动效时间线。
- 不要改 BLE、协议、参数读写逻辑；只规划表现层和交互层。

### 0.1 核心决策

| 项 | 决策 |
|---|---|
| 方向 | `P1 COCKPIT`：车载 CRT 服务终端，不是普通 App |
| 主参考 | eDEX-UI 的 shell/剧场，btop 的面板/数据语法 |
| 布局 | 固定 Top Rail + Context Rail + Function Bar；中间单列仪表 |
| 面板 | 1px 框 + 标题行；0 圆角、0 阴影、无填充卡片 |
| 数据 | 等宽数字、tabular、单位缩小、右对齐；分段条优于连续条；表格优先于卡片 |
| 密度 | 仪表 6–7 个模块一屏；油门 8 参数 + 实时值一屏；调校列表带摘要；监控 4 通道 + 测量表 |
| 颜色 | 绿 5 级亮度 + 琥珀（变更/注意）+ 红（故障），无第四色相 |
| 字体 | IBM Plex Mono（拉丁/数字）+ 系统 sans（中文）；正文 12sp，hero 26–28sp |
| 层级 | 字号阶梯比例 ≤1.3；主要靠颜色、字重、对齐、位置分主次 |
| 效果 | 扫描线在背景层；默认 STD；提供 LOW/STD/HIGH |
| 游戏感 | 只放在 boot / link / read / write / flash / fault 六个时刻 |
| 范围 | 4 tab + 链路控制台 + 键盘 + 写入确认；不改协议与状态机 |

### 0.2 密度契约

这是本版最重要的约束，优先于任何“好看”的留白：

- **每屏信息量 ≥ 当前版本的 2 倍**，且不靠滚动。仪表页至少 6 个数据模块；油门页至少 8 个参数 + 3 个实时读数。
- **Hero 不超过 30sp**。速度 26–28sp；电压/电流 14–15sp；正文 12sp；标签 9–10sp。
- **字号阶梯比例 ≤1.3**。不要 10 → 24 → 52 这种“老太太遥控器”比例。
- **层级不靠字号**：用亮绿/暗绿、Medium/Regular、左对齐/右对齐、框线/反白来分主次。
- **表格优先**：能用 `label | value | unit | state` 一行说清的，不要占一张卡。
- **留白有理由**：组内 4–6dp，组间 8dp；单个空白区 >12dp 必须能解释为什么。
- **一屏一个视觉焦点**：可以是速度，但它只比周围大 1.5–2 倍，不是 3 倍。

### 0.3 相对当前的密度提升

| 页面 | 当前 | v0.2 |
|---|---|---|
| 仪表 | 4 块：车速/电压电流、四轮输出、四轮温度、两条迷你曲线；约 14 个数据点 | 7 块：`DRIVE / TRACTION / POWER / THERMAL / LINK / RESPONSE / EVENT`；约 28 个数据点，含功率、轮间偏差、TMAX、ΔT、HOT、MTU、RSSI、MIN/MAX |
| 调校 | 5 行入口 + 一段写入说明 | `PROFILE` 四项摘要 + 5 行子系统（带参数数量）+ 协议条 |
| 油门 | 8 参数分 2 列，需滚动；参数卡较大 | 8 参数全部一屏；`PARAM/READ/EDIT/RANGE/UNIT` 五列表格 + LIVE 条 |
| 监控 | 8 通道按钮 + 空示波器 + 3 行设置 | 4 轨示波器 + 4 行测量表（MIN/MAX/AVG）+ BUF/DROP/RATE 状态条 |
| 设置 | 4 张卡，信息稀疏 | 5 个分区 + 7 项诊断（MTU/PHY/RSSI/ERR/UPTIME/PKT/DROP） |

> 数字是规划目标，不是硬性 KPI；原则是“同样一屏，至少多一倍可读数据”。

---

## 1. 项目上下文

### 1.1 产品

PINTA Phone 是 IUPro 的安卓手机客户端：**驾驶监控 + 参数标定**。

- 目标机型：Samsung Galaxy S24（SM-S9210），1080×2340，**360×780 dp**，480 dpi，竖屏单手。
- 默认中文；技术元数据用英文/十六进制。
- 实时数据目前是模拟的；BLE 链路是真的；CCU 协议层尚未接入。
- 现有 4 个 tab：仪表、调校、监控、设置。
- 调校二级页：油门曲线已实现；布局、转向、电池、控制器为占位。
- 现有设计：深色工业风、琥珀色强调、0 圆角、8% 白发丝边、等宽数字。

### 1.2 必须保留

- 4 个 tab 的信息架构。
- 调校入口 → 二级页的层级。
- 油门页“读取 → 编辑 → 差异确认 → 写入 → 回读校验 → 保存 Flash”的状态机。
- BLE 扫描/连接/日志入口。
- 演示数据模式，并且必须**明确标记**，不能让人误以为是真车数据。
- 360×780 一屏可读；不能靠缩小字号塞内容。

### 1.3 可以改变

- 卡片体系、边框、字体、色板、图标、动效、弹层样式。
- 顶部栏、底部导航、页面标题的视觉语法。
- 仪表页的信息排布（但必须仍是一屏）。
- 设置页增加“显示/沉浸度”选项。

### 1.4 当前实现地图

- [Theme.kt](../app/src/main/java/com/pinta/phone/ui/theme/Theme.kt)：颜色、字体、字号。
- [Common.kt](../app/src/main/java/com/pinta/phone/ui/components/Common.kt)：卡片、顶部栏、指标、四轮条、迷你曲线。
- [DashScreen.kt](../app/src/main/java/com/pinta/phone/ui/DashScreen.kt)：仪表页。
- [TuneScreen.kt](../app/src/main/java/com/pinta/phone/ui/TuneScreen.kt)：调校入口。
- [ThrottleScreen.kt](../app/src/main/java/com/pinta/phone/ui/ThrottleScreen.kt)：油门曲线 + 参数 + 读写。
- [Placeholder.kt](../app/src/main/java/com/pinta/phone/ui/Placeholder.kt)：监控、设置、调校占位。
- [PintaApp.kt](../app/src/main/java/com/pinta/phone/ui/PintaApp.kt)：Shell、tab、底部导航、蓝牙面板。
- [CurveChart.kt](../app/src/main/java/com/pinta/phone/ui/components/CurveChart.kt)：曲线绘制。
- [Keypad.kt](../app/src/main/java/com/pinta/phone/ui/components/Keypad.kt)：数字键盘。

---

## 2. 方向：P1 COCKPIT

### 2.1 世界观

PINTA 是一台**车载 CRT 服务终端**，不是普通 App。

- 它有自己的启动自检、链路握手、读取扫描、写入校验。
- 它说话简短、技术化、双语：`LINK // BLE`、`READ 8/8`、`VERIFY OK`、`FLASH 0x3F2A91C4`。
- 它只有一个绿色磷光通道，加上琥珀和红色作为告警。
- 它的“游戏感”来自**状态变化时的机器反馈**，不是常驻的故障抖动。

### 2.2 参考怎么拆

| 来源 | 拿什么 | 不要拿什么 |
|---|---|---|
| **eDEX-UI** | 全屏驾驶舱 shell、顶部系统条、面板框、中央视口、底部功能键、启动/链路剧场、模块化网格 | 桌面级的 8 列布局、青色霓虹、满屏假故障、小到看不清的字 |
| **btop** | 标题嵌在边框里的面板、密集数据行、分段仪表、sparkline、进程表、状态色、底部键位提示 | 8 面板桌面布局、多彩渐变、8sp 小字 |
| **Alien: Isolation** | CRT 玻璃质感、物理键、扫描线、diegetic 标签、机器外壳感 | 低对比、浑浊色、为了恐怖感牺牲可读性 |
| **Highfleet** | 排版、开关/旋钮、警示条、军事工业布局 | 琥珀主导（本项目以绿色为主） |
| **Severance / MDR** | 粗像素数字、冷官僚绿、极简光标 | 全像素字体、低信息密度 |

### 2.3 反目标

- 不要赛博朋克霓虹：青、紫、洋红一律不要。
- 不要满屏扫描线、持续闪烁、随机 glitch、RGB 色散。
- 不要把桌面驾驶舱硬塞进 360dp：主数据最多两列，允许三列微数据；正文不小于 12sp。
- 不要把速度做成海报字，或让 hero 独占半屏；hero ≤30sp，层级靠颜色/对齐。
- 不要用 ASCII art 或 box-drawing 字符画中文界面；中文对不齐，而且显得廉价。
- 不要每个卡片都描边发光；光是稀缺资源。
- 不要伪造数据、伪造进度、伪造校验值。
- 不要 Material 默认圆角、elevation、ripple、NavigationBar 直接上屏。

---

## 3. 体验原则

1. **Diegetic shell, readable data.**  
   外壳像机器，数据像仪表；氛围不能牺牲读数。

2. **Frame, don’t card.**  
   用 1px 框、标题行、分隔线分组，不用圆角填充卡片堆叠。

3. **Density is professionalism.**  
   每屏至少 6 个数据模块；组内 4–6dp，组间 8dp；不靠滚动和留白撑场面。

4. **Hierarchy is alignment, not scale.**  
   先靠亮/暗、粗/细、左/右对齐、框线/反白分主次；字号只是最后一层。

5. **Game feel in state, not idle.**  
   启动、链路、读取、写入、故障是“游戏时刻”；静止时保持稳定。

6. **One phosphor, three severities.**  
   绿=正常/测量；琥珀=变更/注意；红=故障。不要第四种色相。

7. **Thumb-first.**  
   主要动作在底部 72dp 操作台；触控目标 ≥48dp；顶部只放状态和低频入口。

8. **Effects are optional.**  
   提供 `LOW / STD / HIGH` 沉浸度；尊重“减少动态效果”。

---

## 4. 全局 Shell

### 4.1 结构

```text
┌──────────────────────────────────────────┐  ← 6dp 外边距 + 1px 外框
│ PINTA // FIELD CONSOLE        [LINK 2M]  │  34dp  Top Rail
├──────────────────────────────────────────┤
│ LINK 2M · MTU 247 · CAN 4/4 · ERR 00  SIM│  18dp  Context Rail
├──────────────────────────────────────────┤
│ 01 // DRIVE                      [RUN]   │  22dp  Page Header
│                                          │
│  ┌ 01 // SPEED ───────────────────────┐  │
│  │                                   │  │
│  │           page content            │  │
│  │                                   │  │
│  └───────────────────────────────────┘  │
│                                          │
├──────────────────────────────────────────┤
│   01 仪表    02 调校   03 监控   04 设置  │  52dp  Function Bar
└──────────────────────────────────────────┘
```

### 4.2 尺寸

- 外边距：6dp；面板间距：6dp；面板内边距：8dp。
- Top Rail：34dp；Context Rail：18dp；Page Header：22dp；Function Bar：52dp。
- 详情页操作台：64–72dp，固定在 Function Bar 之上；详情页建议隐藏 Function Bar。
- 最小触控：48×48dp；导航项整格可点。
- 所有边框 1px；激活边框 1px `frameHot` + 6dp 角标；不使用 2px 粗框（除非焦点态）。

### 4.3 Top Rail

- 左：`PINTA // FIELD CONSOLE`，10sp mono，letter-spacing 0.12em。
- 右：`[LINK 2M]` 状态 token；整块 44dp 高可点，打开链路控制台。
- 状态：`[LINK 2M]` 亮绿、`[LINK WAIT]` 琥珀、`[LINK LOST]` 红、`[NO LINK]` 暗绿。
- 下方 1px `frame`。

### 4.4 Context Rail

- 18dp，10sp mono；左右两端信息，中间用 `·` 分隔。
- 仪表/监控：`LINK 2M · MTU 247 · CAN 4/4 · ERR 00`。
- 调校：`PROFILE IUPro Q003 · 111 VAR / 44 REF`。
- 油门：`REFDATA · 8 PARAMS · Q1_14 OUT`。
- 设置：`SYS · v0.1.0 · DARK ONLY`。
- 演示模式：右端固定 `SIM`，琥珀色；不要只靠一个小点。
- 故障时：`ERR` 段变红；不要整条变红，保留其他信息。

### 4.5 Page Header

- 左：`01 // DRIVE`，10sp mono，index 暗绿，title 亮绿。
- 右：状态 token，如 `[RUN]`、`[READY]`、`[UNREAD]`、`[FAULT]`。
- 下方不画线；让面板自己框住内容。

### 4.6 Function Bar

- 4 等分，52dp；顶部 1px `frame`。
- 每项：16dp 线性图标 + 10sp 中文标签；整格可点。
- 选中：36dp 高的反白块（`phosphor` 底、黑字/黑图标），左右各留 6dp；不要用 Material pill。
- 未选中：`textDim`；按下时 1 帧亮度提升，无 ripple。
- 详情页建议隐藏，改用 Top Rail 左侧 `[← RETURN]`；这样操作台不挤。此项待确认。

### 4.7 Panel 语法

```text
┌ 01 // SPEED ────────────────────── [OK] ┐
│                                         │
│   label                       value     │
│   ██████████░░░░░░  49%                 │
│                                         │
└─────────────────────────────────────────┘
```

- 外框 1px `frame`；无填充，或 4% `bgRaised`。
- 标题行 20dp：`01` 暗绿、`//` 暗绿、标题亮绿；右侧状态 token。
- 标题行下 1px `grid` 分隔线。
- 内容 8dp padding；行高 24–40dp。
- 激活面板：边框 `frameHot`，四角 6dp 角标；不填充、不发光。
- 面板不是卡片：不要圆角、阴影、渐变背景。

---

## 5. 页面规划

> 下面的框线只用于示意布局，**不要用 box-drawing 字符实现**；全部用 Compose 的 1px 线/边框绘制。

### 5.1 仪表 / DASH

**目标**：一屏回答“车现在怎么样”，并且像一份实时工程遥测，不像四张大字海报。

```text
01 // DRIVE                      SIM  [RUN]
┌ DRIVE ─────────────────────────────────┐
│ 15.0 km/h    V 72.4  I 36.0  P 2.61 kW │
│ THR 49% ██████████░░  STR +15° ──●──  │
├ TRACTION ──────────────────────────────┤
│      OUT              TEMP   ΔAVG       │
│ FL ████████░░ 48%     44°C    0        │
│ FR ███████░░░ 47%     43°C   -1        │
│ RL ████████░░ 48%     48°C    0        │
│ RR ████████░░ 49%     47°C   +1        │
├ POWER ───── THERMAL ───── LINK ────────┤
│ V 72.4     TMAX 48°C     PHY 2M        │
│ I 36.0     ΔT 5.0°C      MTU 247       │
│ P 2.61kW   HOT RL        RSSI -42      │
│            LIM 85°C      CAN 4/4       │
├ RESPONSE ──────────────────────────────┤
│ THR 49% MIN 12 MAX 87  ╱╲╱──────────   │
│ STR +15° MIN -18 MAX 22 ────╱╲──────   │
├ EVENT ─────────────────────────────────┤
│ 12:04:07 CAN 4/4 · 12:04:08 SYS OK     │
└────────────────────────────────────────┘
```

**MUST**

- 速度 26–28sp，**不是 38/52sp**；电压/电流/功率 14–15sp；单位 9–10sp；正文 12sp，标签 9–10sp。
- Hero 区域高度 ≤18% 屏高；速度只比周围数据大 1.6–1.9 倍，不做海报字。
- 一屏至少 6 个模块：`DRIVE`、`TRACTION`、`POWER`、`THERMAL`、`LINK`、`RESPONSE`、`EVENT`。
- `POWER / THERMAL / LINK` 做三列微数据区，每列 3–4 行；主数据区最多两列。
- 四轮用一张表：`OUT` 分段条 6dp、百分比 11sp、温度 10sp、`ΔAVG` 9–10sp。
- `ΔAVG = wheel - average`，正值 `+`，负值 `-`；用于看四轮平衡。
- `P = V × I` 实时计算；`TMAX / ΔT / HOT` 从四轮温度派生；`MIN/MAX` 从历史 trace 取；不要伪造。
- 温度 ≥70°C 变琥珀，≥85°C 变红；行尾加 `!`，不只变色。
- 底部 `EVENT` 一行 20dp；显示最近一条真实状态变化，没有就显示 `SYS OK`。
- 演示模式：Context Rail 固定 `SIM`。
- 360×780 不滚动；360×640 只允许压缩 `RESPONSE` 高度和 `EVENT` 行，不允许隐藏关键数据。

**SHOULD**

- 四轮条做 20 段，每段 2dp 间隙；比连续条更像仪表。
- `THR / STR` 用分段条，而不是只放数字；转向条中心为零点，左右各 10 段。
- 所有数值右对齐、单位左对齐；小数点位数固定（速度 1 位、电压 1 位、电流 1 位、功率 2 位、温度 0 位）。
- 事件行用 10sp mono，时间戳暗绿，事件亮绿；故障时前缀 `!`。
- 速度下方保留 1px 细分隔，但不要大留白。

**MAY**

- 增加 `LOOP` / `PKT/s` / `UPTIME`，但必须有真实来源，不能编。
- 四轮条变化超过 5% 时闪一帧亮绿。
- 速度用 DSEG7 仅限 28sp 以内，且三位数不溢出；否则 IBM Plex Mono。

### 5.2 调校 / TUNE

**目标**：像选择“子系统”，不像打开设置列表。

```text
02 // PARAMETERS                    [READY]
┌ PROFILE ────────────────────────────────┐
│ VAR 111   REF 44   NODES 5   LAST 12:04 │
│ IUPro Q003 · FW v1.260406.3 · 44 REF    │
├ SUBSYSTEMS ─────────────────────────────┤
│ 01 整车布局       4/4 BOUND   8 PARAM > │
│ 02 油门曲线       READY       8 PARAM > │
│ 03 转向与差速     READY       9 PARAM > │
│ 04 电池与保护     UNREAD     20 PARAM > │
│ 05 控制器与固件   5 NODES     5 NODE  > │
├ PROTOCOL ───────────────────────────────┤
│ READ > EDIT > WRITE > FLASH      [HELP] │
└─────────────────────────────────────────┘
```

**MUST**

- 顶部 `PROFILE` 摘要：`VAR / REF / NODES / LAST READ` 四个微数据；没有读取过就显示 `--`，不要编时间。
- 5 行子系统全部一屏可见；行高 48dp；1px 分隔线，无卡片。
- 每行显示：index、标题、状态、参数数量、chevron；标题 13sp，元数据 10sp。
- 状态语义：`READY` 绿、`UNREAD` 暗绿、`CHANGED` 琥珀、`LOCKED` 暗绿 + `—`。
- 底部协议条 28dp：当前阶段亮绿，其余暗绿；右侧 `[HELP]` 打开说明。
- 二级页隐藏 Function Bar，Top Rail 显示 `[← RETURN]`（建议，待确认）。
- 不允许为了“呼吸感”把 5 行拆成 5 张大卡；这是一张子系统表。

**SHOULD**

- 行内参数数量右对齐；状态左对齐；不要混在一起。
- 点击行的按下态用反白，而不是 ripple。
- 有改动时，顶部摘要出现 `CHANGED 3` 琥珀 token。

### 5.3 油门曲线 / THROTTLE

**目标**：曲线是主角，参数是仪表读数，读写是控制台操作。

```text
02.1 // THROTTLE CURVE              [READY]
LIVE  V 72.4  I 36.0  T 48°C  LINK 2M
┌ CURVE ─────────────────────────────────┐
│ 100% ┤          ╱                       │
│  50% ┤      ╱──■                       │
│   0% └──────────────────                │
│ IN 49%  OUT 49%  AD 1746  TP .45/.50    │
├ PARAMETERS ────────────────────────────┤
│ PARAM     READ    EDIT    RANGE    UNIT │
│ UV        200     200     0..4094  AD   │
│ RELEASE   220     220     0..4095  AD   │
│ DEPRESS   3770    3770    0..4095  AD   │
│ OV        4095    4095    1..4095  AD   │
│ LOW_DZ    80      80      0..4095  AD   │
│ HIGH_DZ   80      80      0..4095  AD   │
│ TP_X      0.45    0.45    .05-.95       │
│ TP_Y      0.50    0.50    .02-1.00      │
├ ACTION ────────────────────────────────┤
│ IDLE · READ REQUIRED        3 CHANGED   │
│ [ READ ]      [ WRITE 3 ]      [ SAVE ] │
└─────────────────────────────────────────┘
```

**MUST**

- 一屏显示全部 8 个参数，不滚动；默认 360×780 下曲线 150dp，表格 8 行 × 30dp。
- 顶部 `LIVE` 条 18dp：`V / I / Tmax / LINK` 四个实时值；让调参时仍能看到车况。
- 曲线 150dp 高；8×8 网格；主网格 24%，次网格 10%；轴标签 10sp。
- 曲线 1.5dp；方形折点把手 12dp；拖拽热区 ≥48dp。
- 输入游标：1px 虚线十字 + 5dp 方点；不要用圆点。
- `IN / OUT / AD / TP` 四个读数在曲线下方一行；OUT 用亮绿。
- 参数表用 5 列：`PARAM | READ | EDIT | RANGE | UNIT`；行高 30dp，标签 9–10sp，值 12sp。
- `READ` 暗绿，`EDIT` 亮绿；改动项 `EDIT` 变琥珀，行尾 `Δ`，不要整行换边框。
- `RANGE` 必须来自真实允许范围，不允许猜；`TP_X/TP_Y` 显示 2 位小数。
- 底部 Action Bar 64–72dp；状态行 14dp；按钮 48dp。
- Ready / Busy / Done / Failed 四态必须可见；进度用 `[██████----] 6/8`。
- `SAVE` 只在 Flash pending 时变琥珀；否则暗绿禁用。
- 数字键盘用底部控制台抽屉；确认写入用 `WRITE CHECK` 模态，不用 Material AlertDialog。

**SHOULD**

- 曲线区域加一次性的 120ms 扫描线扫过，作为“读取完成”的反馈；不要循环。
- 写入时逐项高亮参数行，与 `6/8` 同步。
- `WRITE` 按钮文案带数量：`WRITE 3`；无改动时禁用并显示 `NO CHANGE`。
- 360×640 时曲线降到 120dp、表格行降到 28dp，仍保留全部 8 行和关键列。

**MAY**

- 折点拖动时显示 `TP_X / TP_Y` 浮标。
- Flash 保存成功后显示一次校验值，1.5s 后淡出。
- 表头右侧加排序/过滤；当前 8 项不需要。

### 5.4 监控 / SCOPE

**目标**：像示波器，不像图表页。

```text
03 // SIGNAL                     [LOCK 4CH]
┌ CHANNELS ──────────────────────────────┐
│ [CH1] [CH2] [CH3] [CH4]                │
│ [CH5] [CH6] [CH7] [CH8]                │
├ SCOPE ─────────────────────────────────┤
│  grid + CH1 ━━  CH2 ┄┄  CH3 ···  CH4 ─·│
│  T                                      │
│  cursor readout: X 12.4ms  Y -0.82      │
├ MEASURE ───────────────────────────────┤
│ CH   MIN     MAX     AVG     UNIT       │
│ 1    -0.98   0.97    0.02    pu         │
│ 2    -0.72   0.74    0.01    pu         │
│ 3    --      --      --      --         │
│ 4    --      --      --      --         │
├ SAMPLE ────────────────────────────────┤
│ MODE C · 50ms  BUF 82%  DROP 0  RUN     │
└─────────────────────────────────────────┘
```

**MUST**

- 同时最多 4 个通道；8 个通道按钮 2×4，32dp 高，整格可点。
- 4 条线必须用**线型**区分：实线、虚线、点线、点划线；不要用 4 种绿色深浅。
- Scope 区域 200dp；6×5 网格；1px；中心十字线更暗。
- 波形的颜色只有亮绿/暗绿；被选中通道亮绿，其余 48% 亮度。
- 测量表 4 行，显示 `MIN / MAX / AVG / UNIT`；无数据 `--`，不要填 0。
- 底部采样条显示 `MODE / RATE / BUF / DROP / RUN`；`BUF` 和 `DROP` 未实现时显示 `--`。
- 无信号：中心 `NO CARRIER`，块状光标闪烁；有信号：右上 `[LOCK 4CH]`。
- 记录/CSV 未实现：显示 `N/A` 暗绿，不要给可点击假象。

**SHOULD**

- 扫描时一条 1px 垂直线从左到右，900ms 循环，仅扫描中。
- 波形尾部 3 个采样点做 20/10/5% 余辉。
- 十字游标读数固定在右上角，不挡波形；`X` 时间，`Y` 值。
- 4 通道性能不足时回退到 2 通道，但测量表保留 4 行。

**MAY**

- 加 `TRIG` 行：`MODE AUTO / SRC CH1 / LEVEL 0.50`；未实现时整行暗绿。
- 加 `HOLD` / `RUN` 控制；当前先只读。

### 5.5 设置 / SYSTEM

**目标**：像机器服务菜单，不像 App 设置。

```text
04 // SYSTEM                       [SYS OK]
┌ DEVICE ────────────────────────────────┐
│ AUTO CONNECT   LAST DEVICE          >  │
│ PROFILE        v1.260406.3          >  │
│ NODES          IU + 4 CTRL          >  │
├ DISPLAY ───────────────────────────────┤
│ IMMERSION      [LOW][STD][HIGH]        │
│ SCANLINES ON      GLOW ON              │
│ REDUCE MOTION OFF                      │
├ WRITE SAFETY ──────────────────────────┤
│ CONFIRM BEFORE WRITE        ON         │
│ STANDSTILL INTERLOCK        ON         │
│ VERIFY AFTER WRITE          ON         │
├ DIAGNOSTICS ───────────────────────────┤
│ MTU 247   PHY 2M   RSSI -42   ERR 00   │
│ UPTIME 00:12:04   PKT 62/s   DROP 0    │
├ ABOUT ─────────────────────────────────┤
│ BUILD 0.1.0 · CCU 0x48 · SUM8          │
└─────────────────────────────────────────┘
```

**MUST**

- 分组用 1px 框 + 标题行；行高 32–36dp；标签 10–11sp，值 11sp mono。
- 默认 360×780 下，`DEVICE + DISPLAY + WRITE SAFETY + DIAGNOSTICS + ABOUT` 尽量一屏；允许设置页滚动，但首屏必须看到诊断信息。
- `DIAGNOSTICS` 显示：`MTU / PHY / RSSI / ERR / UPTIME / PKT/s / DROP`；未实现的值显示 `--`，不要填 0。
- `IMMERSION` 三段控件：`LOW / STD / HIGH`，默认 `STD`。
- `SCANLINES / GLOW / REDUCE MOTION` 用开关，但视觉做成 `[ON] / [OFF]`，不用 Material Switch。
- 版本、协议、Flash 支持信息保留；不要删技术信息。
- 演示模式令牌 `SIM` 在 Context Rail 一直可见。

**SHOULD**

- `HIGH` 只增加静态强度 + 一次启动扫描；不要增加持续闪烁。
- `REDUCE MOTION` 打开时关闭 boot、page wipe、fault pulse，但保留静态扫描线（如果 SCANLINES 仍开）。
- 诊断区没有数据时整行暗绿，不要展示“假在线”。

### 5.6 链路控制台 / LINK CONSOLE

**目标**：像接入一台设备，不像蓝牙设置页。

```text
LINK // BLE                         [CLOSE]
STATE  SCANNING...           [ SCAN ]
┌ TARGETS ───────────────────────────────┐
│ > XY-MBD22A   C4:...:2A   ▂▄▆█  -42 dBm│
│   XY-MBD22A   C4:...:7F   ▂▄░░  -67 dBm│
├ LOG ───────────────────────────────────┤
│ > SCAN START                           │
│ > FOUND 2                              │
│ > CONNECT C4:...:2A                    │
│ > MTU 247 / PHY 2M / SUBSCRIBE OK      │
└─────────────────────────────────────────┘
```

**MUST**

- 全屏覆盖层，不是 Material BottomSheet；顶部 `LINK // BLE` + `[CLOSE]`。
- 目标行 52dp；选中反白；RSSI 用 4 段竖条 + dBm 数字。
- 日志 80dp，5 行，10sp mono；自动滚到底；前缀 `>`。
- 连接过程必须显示真实阶段：`SCAN`、`TARGET`、`PAIR`、`MTU`、`PHY`、`SUBSCRIBE`。
- 失败：红色 `! LINK LOST` banner + 一次 80ms 水平撕裂；不要循环 glitch。
- 扫描中：列表左侧一条 1px 扫描线，900ms 循环；停止扫描即停。

**SHOULD**

- 连接成功后，`LINK` token 闪一帧亮绿 + 轻触觉。
- 设备名过长时保留前缀，地址尾部省略；不要换行。

### 5.7 数字键盘 / ENTRY

**MUST**

- 底部抽屉，高约 60%；0 圆角；顶部 1px `frameHot`。
- 标题 `ENTRY // UV`；当前值 30–32sp mono；块状光标在末尾。
- 范围提示 10sp 暗绿；非法输入时值变红，不弹 Toast。
- 4×3 键，56dp 高；按键 0 圆角、1px 边框；按下反白。
- 底部 `[ CANCEL ]` / `[ ENTER ]` 52dp；ENTER 反白。
- 返回键关闭；不改变已输入值。

**SHOULD**

- 键帽角落放 8sp 的装饰性键位编号（`1`…`9`），只做控制台质感，不承载信息。
- 按下时 8ms 轻触觉；不要每个数字都响。

### 5.8 写入确认 / WRITE CHECK

**MUST**

- 居中模态 320dp 宽；80% 黑遮罩；1px `frameHot`；无圆角。
- 标题 `WRITE CHECK // 3 ITEMS`；下面 old → new 差异表，12sp mono。
- 底部 `[ CANCEL ]` / `[ CONFIRM ]` 48dp；CONFIRM 琥珀反白。
- 说明一行：`回读校验后仍需单独 SAVE FLASH`。
- 不允许点击遮罩关闭；只能 CANCEL / CONFIRM。

### 5.9 启动 / BOOT

**目标**：一次性的“机器开机”时刻，不是每次切 tab 都演。

```text
PINTA // FIELD CONSOLE
P1 PHOSPHOR ........ OK
BLE LINK ........... WAIT
CAN BUS ............ 4/4
MOUNT PROFILE ...... OK
COCKPIT READY
```

**MUST**

- 仅冷启动；900ms 上限；点击任意处跳过。
- 从后台恢复不播；切 tab 不播。
- BLE 未连接显示 `WAIT`，不阻塞进入仪表。
- 播完后 `SIM` token 出现在 Context Rail。
- 不要伪造不存在的硬件自检项；只显示实际状态。

---

## 6. 视觉系统

### 6.1 颜色

| Token | Hex | 用途 |
|---|---|---|
| `bg` | `#030805` | 全局背景，近黑带绿 |
| `bgRaised` | `#07110B` | 面板局部底色，最多 4% 使用 |
| `grid` | `#0F2A1A` | 图表网格、分隔线 |
| `frame` | `#1B4A2C` | 1px 面板框 |
| `frameHot` | `#58FF8C` | 激活框、焦点、角标 |
| `text` | `#CFFFE0` | 主文字，偏白绿 |
| `textDim` | `#7FAE8D` | 次级文字 |
| `textFaint` | `#5E8C70` | 元数据、非关键标签 |
| `phosphor` | `#4DFF7C` | 关键值、活动点、选中反白底 |
| `phosphorHi` | `#B6FFC9` | hero 数字、反白文字 |
| `amber` | `#FFB020` | 变更、注意、待确认、Flash 保存 |
| `red` | `#FF4D4D` | 故障、错误、破坏性操作 |
| `black` | `#000000` | 反白文字/图标 |
| `overlay` | `#000000` 80% | 模态遮罩 |

规则：

- 只允许以上色相。不要蓝、青、紫、洋红。
- 绿色分 5 级亮度：100% 关键值 / 72% 正文 / 48% 标签 / 24% 网格 / 12% 选中底。
- 琥珀只给“变更/注意/待确认/Flash”，不要做普通主按钮。
- 红色只给“故障/错误/破坏性”，不要做装饰。
- 主按钮用**反白**，不是琥珀填充。
- 颜色不能单独承载状态：必须加文字/形状/图标。

### 6.2 字体

| 角色 | 字体 | 字号 | 字重 | 字距 | 用途 |
|---|---|---|---|---|---|
| `hero` | IBM Plex Mono | 26sp | SemiBold | -0.01em | 车速；最大 28sp |
| `dataXL` | IBM Plex Mono | 18sp | Medium | -0.01em | 功率/关键值 |
| `dataL` | IBM Plex Mono | 15sp | Medium | 0 | 电压/电流 |
| `dataM` | IBM Plex Mono | 12sp | Medium | 0.01em | 表格值 |
| `body` | Noto Sans SC / system sans | 12sp | Regular | 0 | 中文正文 |
| `title` | Noto Sans SC / system sans | 13sp | SemiBold | 0 | 页面标题 |
| `label` | IBM Plex Mono | 9sp | Medium | 0.12em | 英文/技术标签，大写 |
| `meta` | IBM Plex Mono | 10sp | Regular | 0.06em | 地址、日志、元数据 |
| `key` | IBM Plex Mono | 20sp | Medium | 0 | 键盘 |
| `caption` | IBM Plex Mono | 9sp | Regular | 0.04em | 轴标签、单位说明 |

规则：

- 字号阶梯：9 / 10 / 12 / 15 / 18 / 26；相邻级别比例 ≤1.5。
- 正文最小 12sp；交互标签最小 10sp；纯技术标签/坐标轴可 9sp。
- **Hero 禁止超过 30sp**；26sp 是默认。速度只比正文大 2 倍左右，不是 3 倍。
- 中文用系统 sans，避免把 Noto Sans SC 全量打进 APK；拉丁/数字用 IBM Plex Mono。
- 数字开启 tabular figures；单位是数值的 60–70% 大小，底部对齐。
- 中文标签字距 0.08em；英文大写标签 0.12em。
- 不要用像素字体排中文；DSEG7 默认不用，除非真机验证 26sp 下仍清晰且不溢出。
- 字重只用 400/500/600；不要到处 700。
- 层级优先级：位置/对齐 > 颜色 > 字重 > 字号。不要靠字号解决所有层级。

### 6.3 间距与边框

- 基准 4dp；组内 4–6dp；面板内 8dp；页面外边距 6dp；面板间距 6dp。
- 边框 1px；激活 1px `frameHot`；不叠加 2px。
- 圆角 0；无阴影；无渐变面（背景径向微光除外）。
- 角标 6dp，1px，只在激活面板/模态上。
- 触控 48dp；导航 52dp；操作台 64–72dp；行高 24–48dp。

### 6.4 CRT 效果

| 效果 | LOW | STD（默认） | HIGH |
|---|---:|---:|---:|
| 扫描线 | 1.5% | 2.5% | 4% |
| 暗角 | 6% | 10% | 15% |
| 静态噪点 | 0% | 1.5% | 2.5% |
| 辉光 | 关 | 仅 hero | hero + 图表 |
| 启动动画 | 关 | 900ms | 1200ms |
| 页面切换 | 无 | 120ms 淡入 | 160ms + 1px 扫描 |
| 磷光重影 | 无 | 无 | 1dp / 8% |

实现规则：

- 扫描线画在**背景层**，不要盖在文字上；周期 3dp，1dp 暗线。
- 暗角是径向透明→12% 黑；不要做屏幕曲率。
- 噪点用静态 64×64 tile，不逐帧随机；避免耗电和闪烁。
- 辉光用 2–3 层低透明度描边/文字阴影，**不要用 `Modifier.blur`**（minSdk 26，效果和性能都不可控）。
- 不做 RGB 色散；最多做绿色磷光重影（HIGH）。
- 不做常驻闪烁；只在状态变化时闪一帧。
- 扫描线覆盖整个 shell，但键盘/确认模态可以关闭扫描线以保证读数。

### 6.5 动效

| 时刻 | 时长 | 备注 |
|---|---:|---|
| 冷启动 | 900ms | 可点击跳过 |
| Tab 切换 | 120ms | 淡入 + 4dp 位移 |
| 面板首次出现 | 160ms | 20ms stagger，最多 4 块 |
| 链路扫描 | 900ms 循环 | 仅扫描中 |
| 连接日志 | 60ms/行 | 3–4 行 |
| 写入单项 | 80ms | 与 `6/8` 同步 |
| 回读校验 | 300ms | 显示 `VERIFY` |
| Flash 保存 | 500ms | 成功后 1 帧闪亮 |
| 故障 | 2×120ms | 然后静止；配触觉 |
| 数值更新 | 0ms | 不做弹跳/滚动 |
| 图表 | 60ms/采样 | 尾部 3 点余辉 |

- 缓动：进度用 linear；页面用 fast-out；不要 spring/bounce。
- 触觉：键盘 8ms；写入确认 12ms；故障 2×20ms。
- 声音：v1 不做；v2 可选，默认关闭。

### 6.6 图标与符号

- 1px 线性图标，16/20dp；不要实心 Material 图标。
- 导航：速度表、滑杆、波形、齿轮；用简单几何。
- 状态符号：`●`、`!`、`▲`、`[ ]`、`>`；只用可靠字体支持的字符。
- 不要 emoji；不要彩色图标。
- 光标：`█` 块，1s 闪烁，只出现在可输入/扫描中；不要做装饰。

### 6.7 文案语气

- 中文为主，英文是技术元数据；不要每行都双语。
- 短、硬、可执行：`READ REQUIRED`、`VERIFY OK`、`NO CHANGE`、`LINK LOST`。
- 错误：`WRITE FAILED / CHECKSUM 0x3F2A`，不要“哎呀出错了”。
- 成功：`WRITE OK / 8 OF 8`，不要“太棒了”。
- 演示：`SIM / DEMO DATA`，必须常驻。
- 时间 24h；地址/校验值用 mono；单位不翻译。

---

## 7. 组件清单

| 组件 | 结构 | 状态 | 备注 |
|---|---|---|---|
| `CrtSurface` | 背景、扫描线、暗角、噪点、内容 | low/std/high | 根层，只画一次 |
| `ConsoleFrame` | 1px 外框 + 4 角标 | 默认/激活 | 页面最外层 |
| `TopRail` | 标题 + 链路 token | connected/wait/lost | 链路区 44dp 可点 |
| `ContextRail` | 左上下文 + 右 `SIM` | normal/sim/fault | 20dp |
| `PageHeader` | index + title + token | default | 22dp |
| `Panel` | 框 + 标题行 + 内容 | default/active/warn | 无填充 |
| `DataRow` | label/value/unit/state | normal/changed/warn | 值右对齐 |
| `SegmentBar` | 20 段 + 阈值刻度 | normal/warn/crit | 8dp 高 |
| `Sparkline` | 网格 + 线 + 余辉 | live/idle | 1.5dp 线 |
| `Scope` | 网格 + 4 轨 + 触发 | lock/no carrier | 200dp |
| `KeyCap` | 48/56dp 键 | default/pressed/disabled | 0 圆角 |
| `StatusToken` | `[TEXT]` | ok/warn/error/dim | 10sp mono |
| `ProgressBlocks` | `[████----] 6/8` | busy/done/fail | 等宽 |
| `LogTail` | 5 行 mono | live/frozen | 80dp |
| `ModalConsole` | 全屏/居中控制台 | open/close | 无 Material 圆角 |
| `ImmersionControl` | LOW/STD/HIGH | selected | 三段 |

---

## 8. 技术约束

- Compose + Material3；minSdk 26；无图表库；无新依赖优先。
- 根层 `CrtSurface` 用 `drawWithContent`：背景 → 扫描线 → 内容；噪点/暗角在内容下方。
- 不要用 `Modifier.blur`；辉光用多层 alpha 描边/文字阴影。
- 扫描线/噪点用静态 tile；不要每帧生成随机数。
- 遥测 60ms 更新；图表用 ring buffer；避免整屏重组。
- 字体：打包 IBM Plex Mono 3 个字重（Regular/Medium/SemiBold，约 300KB）；中文用系统 sans；DSEG7 可选。
- 所有自绘组件用 `Canvas`，线宽用 `dp.toPx()`，保证 480dpi 下 1px 视觉一致。
- 弹层不要用 Material `AlertDialog` / `ModalBottomSheet` 默认外观；用 `Dialog` + 自绘，或 `ModalBottomSheet` 但去掉圆角/拖柄。
- 保留现有状态机与 BLE 逻辑；只替换 UI 层。

### 8.1 实施顺序建议

> 进度（2026-10-26）：**仪表页已按 v0.2 落地**（7 模块、`MOTOR FB` 四电机 Iq/Ibus/ω、26sp hero、12sp 正文、360×780）；其余页面仍保留旧布局，只换了绿色 token。

1. `Theme.kt` token + `CrtSurface` + `ConsoleFrame` + `Panel`。
2. Shell：Top Rail / Context Rail / Function Bar。
3. Dash 一屏 → 截图评审。
4. Tune 列表 → Throttle 曲线/参数/操作台。
5. Keypad / Write Check / Link Console。
6. Scope / Settings。
7. 动效、沉浸度开关、真机测试。

---

## 9. 验收标准

- [ ] 360×780 仪表一屏至少 6 个模块：`DRIVE / TRACTION / POWER / THERMAL / LINK / RESPONSE / EVENT`，并显示速度、电压、电流、功率、四轮扭矩/温度/偏差、油门/转向、链路/故障、演示标记。
- [ ] 油门页一屏显示全部 8 个参数（`PARAM / READ / EDIT / RANGE / UNIT`），默认不滚动。
- [ ] 调校页一屏显示 `PROFILE` 四项摘要 + 5 个子系统行；监控页显示 4 通道 + 4 行测量表。
- [ ] 速度 ≤28sp，Hero 不超过 30sp；相邻字号比例 ≤1.5；正文 12sp；不允许 38/52sp 海报字。
- [ ] 4 个 tab 页面 + 链路控制台 + 键盘 + 写入确认共用同一 shell。
- [ ] 没有 Material 默认圆角卡片、AlertDialog、NavigationBar、Switch 直接上屏。
- [ ] 主要触控 ≥48dp；Top Rail 的链路入口可视觉 34dp，但触控区 ≥44dp；正文 ≥12sp；交互标签 ≥10sp；纯技术标签/坐标轴可 9sp。
- [ ] 字体缩放 1.15 下不裁切、不重叠。
- [ ] 颜色语义固定：绿正常/测量，琥珀变更/注意，红故障；无第四色相。
- [ ] 扫描线在背景层；无持续闪烁；`REDUCE MOTION` 可关闭 boot/wipe/pulse。
- [ ] 演示模式始终有 `SIM` 标记。
- [ ] 写入/保存/故障状态真实反映流程，不伪造进度。
- [ ] 60fps 滚动；无整屏重组导致的卡顿。
- [ ] 360×780 与 1080×2340 各出一组截图。

---

## 10. 给规划 Agent 的交付物

按顺序做，不要一次铺满：

1. **`CONCEPT.md`（1 页）**  
   世界观、3 条设计支柱、参考拆分、反目标、方向名 `P1 COCKPIT`。

2. **`TOKENS.md`**  
   颜色、字体、字号、间距、边框、效果等级、动效时长；全部给精确值。

3. **Dash wireframe（360×780）**  
   一张默认态、一张故障态、一张演示态；标出所有尺寸、模块数量、每个模块的数据点数量，并附一行“相对当前版本增加了什么”。

4. **其余 wireframe**  
   Tune、Throttle、Scope、Settings、Link Console、Keypad、Write Check；每页至少 default + 一个特殊态。

5. **`COMPONENTS.md`**  
   每个组件的 anatomy、尺寸、状态、按下态、禁用态、Compose 映射。

6. **`MOTION.md`**  
   Boot / Link / Read / Write / Flash / Fault 六条时间线；标明触发条件和结束状态。

7. **`IMPLEMENTATION_PLAN.md`**  
   分阶段任务、涉及文件、风险、需要真机验证的点。

8. **可选：可点击原型**  
   如果时间允许，做一个静态 Compose `Preview` 或 HTML 原型，只覆盖 Dash + Throttle。

**完成定义**：另一个开发者拿到文档后，不需要再问“这个组件在按下/禁用/故障时长什么样”就能实现。

---

## 11. 需要确认的问题

1. 详情页（Tune 子页 / Throttle）是否隐藏底部 4 tab？建议隐藏，换成 `[← RETURN]`。
2. 冷启动动画是否每次都要？建议只冷启动，900ms，可跳过。
3. 是否要音效？建议 v1 不做，v2 默认关闭。
4. 车速是否用 DSEG7？建议先做 IBM Plex Mono，真机看宽度再决定。
5. 监控页是否接受“最多 4 通道同屏”？建议接受；4 条线靠线型区分，8 条在 360dp 上不可读。
6. `IMMERSION` 默认 `STD` 是否合适？建议是；`HIGH` 给夜间/展示。
7. 方向名 `P1 COCKPIT` 是否保留？可以换成更短的产品代号。

---

## 12. 参考链接

- [eDEX-UI](https://github.com/GitSquared/edex-ui)：驾驶舱 shell、系统监控、链路剧场。
- [btop](https://github.com/aristocratos/btop)：面板语法、密集数据行、分段仪表。
- [Alien: Isolation](https://store.steampowered.com/app/214490/Alien_Isolation/)：CRT 材质、物理键、diegetic UI。
- [Highfleet](https://www.rockpapershotgun.com/highfleet-has-the-most-gorgeous-menu-switches)：排版、开关、警示条。
- [AIUI Monochrome-Green](https://github.com/yodaos-project/AIUI/blob/main/design/monochrome/design-system-green.md)：单色绿亮度阶梯、1px 线、状态冗余。
- [cool-retro-term](https://github.com/Swordfish90/cool-retro-term)：扫描线/曲率/辉光参数（只取 10%）。
- [Interface In Game](https://interfaceingame.com/)：游戏 UI 截图库。
- [Territory Studio](https://territorystudio.com/)：电影 UI 工作室案例。
