# PINTA // P1 COCKPIT — 实现记录 v1.4

**记录日期**：2026-10-27  
**目标**：在其它窗口/项目复现当前座舱页（360×780 dp）  
**基线截图**：真机截图属于本地中间产物，不入库（`docs/images/` 已在 `.gitignore`）。

**代码基线**：

- [Theme.kt](../app/src/main/java/com/pinta/phone/ui/theme/Theme.kt)
- [DashScreen.kt](../app/src/main/java/com/pinta/phone/ui/DashScreen.kt)
- [Telemetry.kt](../app/src/main/java/com/pinta/phone/core/Telemetry.kt)
- [PintaApp.kt](../app/src/main/java/com/pinta/phone/ui/PintaApp.kt)
- [MainActivity.kt](../app/src/main/java/com/pinta/phone/MainActivity.kt)
- [CockpitIcon.kt](../app/src/main/java/com/pinta/phone/ui/icons/CockpitIcon.kt)

> 复现时先按本文的骨架和尺寸做，再对照截图微调。  
> 最容易翻车的两点：**`lineHeight = 1.em`** 和 **固定高度/滚动阈值**，见第 8 节。

---

## 0. 快速复现顺序

1. 复制 `Theme.kt` 的颜色/Typography token。
2. 添加 `CockpitIcon.kt`，底部导航用 `座舱` + 17dp 图标。
3. 在 `Telemetry` 增加 IO/电机字段和 demo 数据。
4. 按第 3–7 节搭 `DashScreen`：Header → Drive → IO → MotorGrid → Event。
5. `MainActivity` 设置深色系统栏；`PintaApp` 底部导航使用 52dp 行。
6. 360×780 真机截图对照；先保证不裁切，再调密度。

---

## 1. 文件职责

| 文件 | 职责 |
|---|---|
| `Theme.kt` | 颜色 token、全局 Typography、Material scheme |
| `DashScreen.kt` | 座舱页全部：Header、Drive、IO、MotorGrid、MotorCurves、Event、CRT backdrop |
| `Telemetry.kt` | 数据模型 + demo 数据源 + `rememberTrace` 环形缓冲 |
| `PintaApp.kt` | 底部导航、tab 路由、BLE sheet；仪表 tab 改 `座舱` |
| `MainActivity.kt` | edge-to-edge、深色状态栏/导航栏图标 |
| `CockpitIcon.kt` | 17dp 三辐方向盘 ImageVector |

---

## 2. 颜色与字体 Token

### 2.1 颜色

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

用法：

- `Ink`：全屏背景。
- `SurfaceLow.copy(alpha = 0.18f/0.28f)`：IO/电机格背板。
- `Phosphor`：活动按键、Iq 曲线、正常状态。
- `Amber`：Ibus 曲线、注意/变更；不再用于按键 active 背景。
- `TextPrimary`：ω 曲线、主数值。
- `Danger`：故障 flag、故障 banner。
- `Hairline`：所有 1px 框、分隔线、图表网格。

### 2.2 Typography

```kotlin
val Mono = FontFamily.Monospace
val Sans = FontFamily.SansSerif

// displaySmall 26sp SemiBold -0.4sp tnum
// headlineMedium 18sp Medium
// titleLarge 15sp Sans SemiBold
// titleMedium 13sp Sans Medium
// bodyMedium 12sp Sans
// bodySmall 10sp Sans
// labelMedium 10sp Mono Medium 0.6sp
// labelSmall 9sp Mono Medium 1.0sp
```

座舱页所有局部字号：

| 位置 | 字号 |
|---|---:|
| 车速 | 26sp SemiBold |
| 页头/面板标题 | 8–10sp |
| IO key glyph | 11sp SemiBold |
| IO key label | 6sp |
| IO analog label/value | 8sp / 10sp |
| IO sense label/value | 6.5sp / 8sp |
| Motor cell name | 10sp |
| Motor CTRL/MOT/fault | 7sp |
| Motor legend | 6sp |
| Event | 8–9sp |

### 2.3 关键：行高

座舱根节点必须：

```kotlin
CompositionLocalProvider(
    LocalTextStyle provides TextStyle(lineHeight = 1.em)
) {
    Box(Modifier.fillMaxSize().background(Ink)) { ... }
}
```

原因：Material3 的 `LocalTextStyle` 默认 lineHeight 约 24sp。只设 `fontSize = 7.sp` 时，行框仍是 24sp，会把 IO 小字、电机格 header、legend 裁掉。

---

## 3. 页面骨架

```kotlin
@Composable
fun DashScreen(link: LinkState) {
    val t by rememberDemoTelemetry()
    val powerKw = t.vbus * t.ibus / 1000f

    // 12 条历史（4 电机 × 3 信号）
    val iqTraces = listOf(
        rememberTrace(t.motorIq.getOrElse(0) { 0f } / 135f),
        // 1,2,3 ...
    )
    // ibusTraces / omegaTraces 同理，分别 /40f /2000f

    CompositionLocalProvider(LocalTextStyle provides TextStyle(lineHeight = 1.em)) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            CrtBackdrop()
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val compact = maxHeight < 630.dp
                    val needScroll = maxHeight < 570.dp || t.faultCode != 0
                    Column(
                        Modifier.fillMaxSize()
                            .then(if (needScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DrivePanel(t, powerKw, Modifier.fillMaxWidth().height(94.dp))
                        IoPanel(t, Modifier.fillMaxWidth().height(if (compact) 140.dp else 146.dp))
                        MotorGrid(
                            t, iqTraces, ibusTraces, omegaTraces,
                            if (needScroll) Modifier.fillMaxWidth().height(300.dp)
                            else Modifier.weight(1f),
                        )
                        EventStrip(t.faultCode, link)
                    }
                }
            }
        }
    }
}
```

### 3.1 高度账本（S24，三键导航，360×780）

| 区域 | 高度 |
|---|---:|
| 状态栏 | 系统 |
| Top Rail | 32dp |
| Page Header | 20dp |
| 内容外边距 | 左右 6dp，上下 4dp |
| Drive | 94dp |
| IO | 140dp（compact）/146dp |
| MotorGrid | 剩余高度，约 331dp；scroll 时 300dp |
| Event | 18dp |
| 模块间距 | 4dp |
| 底部导航 | 52dp + nav bar |

实际 body 约 **603dp**。  
`compact < 630dp`；`needScroll < 570dp || fault`。  
如果换机型，先测 body 高度，再按这个账本重新分配；不要照抄绝对高度。

---

## 4. Header

顶栏由 `PintaApp` shell 持有，固定在所有 tab 内容之上；页面自身不再有 top bar。

```text
PINTA // FIELD CONSOLE                         [NO LINK]
00 // COCKPIT      PHY -- · MTU -- · CAN -- [SIM]
```

切 tab 只换第二行：

| tab | 第二行 | 右侧 context |
|---|---|---|
| 座舱 | `00 // COCKPIT` | `PHY/MTU/CAN` |
| 调校 | `10 // TUNE` | `PROFILE · 111 VAR / 44 REF` |
| 监控 | `20 // SCOPE` | `8 CH · ACQ_C / 1–255 ms` |
| 设置 | `30 // SYSTEM` | `IUPro v1.260406.3 · CONFIG` |

调校二级页：`10.1 LAYOUT`、`10.2 THROTTLE`、`10.3 STEERING`、`10.4 BATTERY`、`10.5 NODES`，左侧 `←` 返回。

- Top Rail：32dp，`statusBarsPadding()`，左右 6dp。
- Page Header：20dp。
- **没有** 独立 Context Rail；context 在 Page Header 右侧。
- 旧的 `DEMO [RUN]` 已删除；`[SIM]` 保留。
- `[NO LINK]` 外层用 `Box(fillMaxHeight(), contentAlignment=Center)`，保证和左侧文字同一条水平中心线。
- 顶栏使用 `CompositionLocalProvider(LocalTextStyle provides TextStyle(lineHeight = 1.em))`。

```kotlin
@Composable
private fun PintaChromeHeader(
    tab: Tab, route: String?, link: LinkState,
    onLinkClick: () -> Unit, onBack: () -> Unit,
) {
    val detail = tab == Tab.Tune && route != null
    val code = when {
        tab == Tab.Dash -> "00"
        tab == Tab.Tune && detail -> "10.$detailCode"
        tab == Tab.Tune -> "10"
        tab == Tab.Scope -> "20"
        else -> "30"
    }
    val title = when {
        tab == Tab.Dash -> "COCKPIT"
        tab == Tab.Scope -> "SCOPE"
        tab == Tab.Setup -> "SYSTEM"
        !detail -> "TUNE"
        route == ROUTE_LAYOUT -> "LAYOUT"
        route == ROUTE_THROTTLE -> "THROTTLE"
        route == ROUTE_STEERING -> "STEERING"
        route == ROUTE_BATTERY -> "BATTERY"
        else -> "NODES"
    }
    // context: cockpit -> PHY/MTU/CAN; tune -> PROFILE; scope -> ACQ; system -> CONFIG

    CompositionLocalProvider(LocalTextStyle provides TextStyle(lineHeight = 1.em)) {
        Column(Modifier.fillMaxWidth().background(Ink).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("PINTA", 10.sp, Bold, color = Phosphor, letterSpacing = 1.2.sp)
                Text(" // FIELD CONSOLE", 8.sp, color = TextFaint)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.fillMaxHeight().clickable(onClick = onLinkClick).padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(linkText, 9.sp, color = linkColor)
                }
            }
            HairlineH()
            Row(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (detail) Text("←", 12.sp, color = TextDim,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 6.dp))
                Text(code, 8.sp, color = TextFaint)
                Text(" // ", 8.sp, color = TextFaint)
                Text(title, 10.sp, Medium, color = Phosphor, letterSpacing = 1.4.sp)
                Spacer(Modifier.weight(1f))
                Text(context, 8.sp, color = TextFaint, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Text("[SIM]", 8.sp, color = Amber)
            }
            HairlineH()
        }
    }
}
```

Shell:

```kotlin
Column(Modifier.fillMaxSize()) {
    PintaChromeHeader(...)
    Box(Modifier.fillMaxSize().weight(1f)) {
        when (tab) { ... screens without top bars ... }
    }
}
```

---

## 5. Drive

- 固定 94dp。
- 左侧：车速 26sp + `km/h` 9sp + THR/STR 分段条（20 段、6dp 高、1dp gap）。
- 右侧：V / I / P 三行，label 8sp、value 12sp、unit 7sp；中间 1px 竖线。
- `P = V × I / 1000`。

关键代码：

```kotlin
Text(t.speedKph.f1(), fontFamily = Mono, fontSize = 26.sp,
     fontWeight = FontWeight.SemiBold, color = TextPrimary)
BarLine("THR", t.throttle, t.throttle.pct(), Phosphor)
CenterBarLine("STR", (t.steerDeg / 30f).coerceIn(-1f, 1f),
              "${t.steerDeg.signed(0)}°", PhosphorHot)
```

---

## 6. IO

### 6.1 结构

```text
┌ 02 // IO ───────────────────────── IU ┐
│ [1][2][3][R][FIX][P][L][R][BRK]        │  weight 1.25, 9 等分
│ [TH | 3.18V]        [STR | 3.75V]      │  weight 1, 2 等分
│ [NTC|..][2.0REF|..][V_IU|..][V_ACC|..][V_12|..] │ weight 1, 5 等分
└────────────────────────────────────────┘
```

- **没有** `KEYS / ANALOG / SENSE` 小标题。
- 三组之间只留 4dp 间距。
- 所有按键 active 背景统一 `Phosphor`（不再用 Amber）。
- demo 循环：
  - 档位 1/2/3 每 2s 轮换；
  - `R / FIX / P / L / R / BRK` 每 1.5s 依次点亮。

```kotlin
val demoGear = 1 + ((t / 2.0).toInt() % 3)
val demoAux = ((t / 1.5).toInt() % 6)

gear = demoGear
reverse = demoAux == 0
repair = demoAux == 1
park = demoAux == 2
turnLeft = demoAux == 3
turnRight = demoAux == 4
brake = demoAux == 5
```

### 6.2 按键格 `KeyCell`

- 9 等分，2dp 间隙，高度由父 Row weight 填满。
- 1px 边框；active 实心 `Phosphor`，黑字/黑图标；inactive 透明底 + `Hairline` 框。
- `glyph` 用于 1/2/3/R/P；`icon` 用于扳手/箭头/刹车；`label` 6sp。

```kotlin
@Composable
private fun KeyCell(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    icon: ImageVector? = null,
    label: String? = null,
) {
    val fg = if (active) Color.Black else TextDim
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(if (active) color else Color.Transparent)
            .border(1.dp, if (active) color else Hairline),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        glyph?.let {
            Text(it, fontFamily = Mono, fontSize = 11.sp,
                 fontWeight = FontWeight.SemiBold, color = fg)
        } ?: icon?.let {
            Icon(it, null, tint = fg, modifier = Modifier.size(12.dp))
        }
        label?.let {
            Text(it, fontFamily = Mono, fontSize = 6.sp,
                 color = if (active) Color.Black.copy(alpha = 0.65f) else TextFaint)
        }
    }
}
```

### 6.3 读数框 `ReadoutBox`

- 1px 外框；左 label、1px 竖线、右 value。
- label/value **各占 1:1 半区**，各自在自己半区水平居中。
- ANALOG 2 等分；SENSE 5 等分。
- 高度由父 Row weight 填满。

```kotlin
@Composable
private fun ReadoutBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelSize: TextUnit = 7.sp,
    valueSize: TextUnit = 8.sp,
    valueColor: Color = TextPrimary,
) {
    Row(
        modifier = modifier.fillMaxHeight().border(1.dp, Hairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = Mono,
            fontSize = labelSize,
            color = TextFaint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(1.dp).height(14.dp).background(Hairline))
        Text(
            text = value,
            fontFamily = Mono,
            fontSize = valueSize,
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}
```

### 6.4 IO 字段

```kotlin
val gear: Int = 1
val reverse: Boolean = false
val repair: Boolean = false
val park: Boolean = false
val turnLeft: Boolean = false
val turnRight: Boolean = false
val brake: Boolean = false
val throttleVolt: Float = 0f       // 0..5V, 2 位
val steerVolt: Float = 2.5f        // 0..5V, 2 位
val ref2vAd: Int = 0               // 4 位，%04d
val vIu: Float = 0f                // 1 位
val vAcc: Float = 0f               // 1 位
val v12: Float = 0f                // 1 位
// NTC 复用 tempC，1 位
```

---

## 7. MotorGrid（03–07）

### 7.1 结构

- 2×2 四格，`FL / FR` 上排，`RL / RR` 下排。
- 四个格完全等大，4dp 间距，占满 03–07 区域。
- 每格：
  - 顶部居中：`03 // FL`
  - 第二行居中：`CTRL 40.4°  MOT 45.4°  [OK]`
  - 1px 分隔线
  - 3 条叠加曲线
  - 底部 legend：`IQ 92.7 / IB 11.6 / ω 1902`

### 7.2 数据与归一化

```kotlin
val iqTraces = listOf(
    rememberTrace(t.motorIq.getOrElse(0) { 0f } / 135f),
    rememberTrace(t.motorIq.getOrElse(1) { 0f } / 135f),
    rememberTrace(t.motorIq.getOrElse(2) { 0f } / 135f),
    rememberTrace(t.motorIq.getOrElse(3) { 0f } / 135f),
)
val ibusTraces = listOf(
    rememberTrace(t.motorIbus.getOrElse(0) { 0f } / 40f),
    // 1,2,3
)
val omegaTraces = listOf(
    rememberTrace(t.motorOmega.getOrElse(0) { 0f } / 2000f),
    // 1,2,3
)
```

- `Iq` 满量程 135A，`Ibus` 40A，`omega` 2000rpm。
- 三条线都归一化到 0..1 后叠加；当前值在 legend 以真实单位显示。
- 不同颜色：
  - `Iq` = `Phosphor #4DFF7C`
  - `Ibus` = `Amber #FFB020`
  - `omega` = `TextPrimary #CFFFE0`

### 7.3 MotorGrid / MotorCell

```kotlin
Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MotorCell("03", "FL", 0, ...)
        MotorCell("04", "FR", 1, ...)
    }
    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MotorCell("05", "RL", 2, ...)
        MotorCell("06", "RR", 3, ...)
    }
}
```

`MotorCell` 关键：

```kotlin
val ctrl = t.controllerTemps.getOrElse(i) { 0f }
val motor = t.wheelTemps.getOrElse(i) { 0f }
val fault = t.motorFaultCodes.getOrElse(i) { 0 }

Text("CTRL ${ctrl.f1()}°", 7.sp, color = TextDim)
Text("MOT ${motor.f1()}°", 7.sp, color = TextDim)
Text(if (fault == 0) "[OK]" else "[E%02X]".format(fault),
     7.sp, color = if (fault == 0) Phosphor else Danger)

MotorCurves(iq, ibus, omega, Modifier.weight(1f))
MotorLegend("IQ ${iq.f1()}", Phosphor)
MotorLegend("IB ${ibus.f1()}", Amber)
MotorLegend("ω ${omega.f0()}", TextPrimary)
```

### 7.4 曲线绘制 `MotorCurves`

纯 Compose `Canvas`，无图表库：

```kotlin
Canvas(modifier) {
    // 1) 3 条竖线 + 3 条横线，Hairline
    for (i in 0..2) {
        drawLine(Hairline, Offset(size.width * i / 2, 0f),
                 Offset(size.width * i / 2, size.height), 1f)
        drawLine(Hairline, Offset(0f, size.height * i / 2),
                 Offset(size.width, size.height * i / 2), 1f)
    }

    fun plot(values: List<Float>, color: Color) {
        if (values.size < 2) return
        val pad = 2.dp.toPx()
        val w = (size.width - pad * 2).coerceAtLeast(1f)
        val h = (size.height - pad * 2).coerceAtLeast(1f)
        val path = Path()
        values.forEachIndexed { index, raw ->
            val x = pad + index * (w / (values.size - 1))
            val y = pad + (1f - raw.coerceIn(0f, 1f)) * h
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
    }

    plot(iq, Phosphor)
    plot(ibus, Amber)
    plot(omega, TextPrimary)
}
```

### 7.5 数据字段

```kotlin
/** dyn_fb[n].tptCtrl */
val controllerTemps: List<Float> = listOf(0f, 0f, 0f, 0f)
/** dyn_fb[n].tptMotor */
val wheelTemps: List<Float> = listOf(0f, 0f, 0f, 0f)
/** dyn_fb[n].errCode */
val motorFaultCodes: List<Int> = listOf(0, 0, 0, 0)

/** dyn_fb[n].Iq_pu × 135A */
val motorIq: List<Float>
/** dyn_fb[n].Ibus_pu × 40A */
val motorIbus: List<Float>
/** dyn_fb[n].omega_pu × 2000rpm */
val motorOmega: List<Float>
```

- 旧 `04 POWER` / `05 THERMAL` / `06 LINK` / `07 RESPONSE` 已移除。
- `06 LINK` 暂时隐藏；顶部链路 token 仍保留。
- `07 RESPONSE` 删除，THR/STR 不再重复展示。

---

## 8. 坑位与约束

### 8.1 必须设 `lineHeight = 1.em`

否则 6–7sp 的 label/legend 行框仍按 24sp 计算，直接裁切。

### 8.2 固定高度 vs 滚动

- S24 三键导航 body 约 603dp。
- 非滚动：Drive 94 + IO 140 + MotorGrid ≈331 + Event 18 + 3×4 gap + 8 padding ≈603。
- `needScroll`：body < 570dp 或存在故障。
- 滚动时 MotorGrid 固定 300dp，避免 weight 在无限高度里失效。
- 不要把所有高度写死到更大屏；先测 body 再分配。

### 8.3 按键 active 统一绿色

不要再用 Amber 做 IO 按键背景；Amber 只给曲线/注意状态。  
`KeyCell` 的 `color` 目前所有调用都传 `Phosphor`。

### 8.4 顶部 context 不再单独一行

`PHY / MTU / CAN / ERR` 在 Page Header 右侧；不要再加回 Context Rail。

### 8.5 顶部 `[NO LINK]`

用 `Box(fillMaxHeight(), contentAlignment = Center)`，不要用 `heightIn(28.dp)` 撑高；否则会偏上。

### 8.6 曲线不要用真实量程直接叠加

三条线单位不同，必须先归一化（/135、/40、/2000），再用 legend 显示真实值。

### 8.7 右侧灰色竖条

真机右侧的灰色竖条是三星 Edge 面板把手，不是 Compose 滚动条，不要为它改布局。

---

## 9. 构建与验证

```powershell
$env:GRADLE_USER_HOME='D:\codex\PINTA_phone\.gradle-home'
$env:GRADLE_RO_DEP_CACHE='C:\Users\wdach\.gradle\caches'
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

验证项：

- [ ] 360×780，三键导航，`EVENT` 完整可见。
- [ ] 02 IO 三组无小标题，9 键全绿；模拟 9 键都会亮。
- [ ] 03–06 四格等大，2×2，无 04/05/06/07 独立面板。
- [ ] 每格 3 条曲线颜色可区分，legend 数值真实单位。
- [ ] CTRL/MOT/`[OK]` 在格子上方居中。
- [ ] 故障码非 0 时显示红色 `[E%02X]`。
- [ ] 顶部 `[NO LINK]` 与左侧文字同水平中心。
- [ ] 无 Material 卡片/圆角/阴影/ripple。

---

## 10. 最小复现代码顺序

1. `Theme.kt` 颜色 + Typography。
2. `CockpitIcon.kt` + `PintaApp` 的 `Dash("座舱", CockpitIcon)`。
3. `Telemetry.kt` 字段 + demo。
4. `PintaApp` shell：
   - `PintaChromeHeader`（32 + 20，固定）
   - `Box(weight(1f))` 内放当前 tab 的页面
5. `DashScreen`：
   - `CompositionLocalProvider(lineHeight = 1.em)`
   - `CrtBackdrop`
   - `DrivePanel`（94）
   - `IoPanel`（140/146）
   - `MotorGrid`（weight / 300 scroll）
   - `EventStrip`（18）
6. `MainActivity` 深色系统栏。
7. 真机截图对照，先修裁切再调密度。

---

## 11. Bluetooth Console

> 真机截图不入库（`docs/images/` 已在 `.gitignore`）。

### 11.1 和旧代码的差异

旧 [BluetoothPage.kt](../../android_PINTA_3/app/src/main/java/com/pinta/app/ui/components/BluetoothPage.kt)：

- 全屏 `Dialog` + 16dp 外边距 + `RoundedCornerShape(16/12/8)`。
- RAW 固定 300dp，输入框 56dp，日志 11sp，GroupBox 12dp padding。
- CCP 带宽结果卡 160dp + LatencyChart，按钮 40dp。
- 在 360dp 宽的手机上信息密度低，必须滚动/裁切。

新 [LinkConsole.kt](../app/src/main/java/com/pinta/phone/ui/LinkConsole.kt)：

- 全屏覆盖 `Ink`，`statusBarsPadding + navigationBarsPadding`。
- 1px `Hairline` 框，0 圆角、无阴影。
- 两种 log 字号 5.5–6sp；按钮 22dp、输入 24dp。
- RAW / CCP 用 `weight(1f)` / `weight(1.15f)` 分剩余高度，不写死 300dp。
- 长按任意 RAW/CCP 行复制整行，Toast 提示。

### 11.2 RAW 数据链路

`Ble.kt` 新增：

```kotlin
enum class FrameDirection { TX, RX }
data class BleFrame(timestamp: Long, direction: FrameDirection, bytes: ByteArray)

val frames: StateFlow<List<BleFrame>>
val mtu: StateFlow<Int>
fun clearFrames()
```

- `send()` 成功后 append TX 原始字节。
- `onRx()` append RX 原始字节，并调用 `Ccu.onData(value)`。
- `onMtuChanged()` 更新 `mtu`。
- 最多保留 240 帧。

RAW 行显示：`HH:mm:ss.SSS TX/RX HEX`；长按把整行复制到剪贴板。

### 11.3 CCP 最小诊断层

`Ccu.kt` 只实现诊断所需，不移植旧 1800 行完整协议：

```text
帧: 0x48 | cmd | len | data[len] | sum8 | 0x54
总长 = len + 5
```

- `onData` 做粘包/拆包、帧尾/校验和检查（XY-MBD22A 会把一帧拆成多条通知，必须跨通知累积）。
- `logs` 记录 TX/RX、命令名、len、dataHex、校验、延迟。
- 请求串行化：`Mutex` 保证同一时刻只有一条在等应答，避免 layout / bandwidth 的 Δ 串线。
- `sendLayoutProbe()` → `readLayout()`：发 `0x04` + `'R' 0xFF 0xFF`，等 `0x05`（54B）：
  ```text
  0x05 | iuConfig | displayConfig | 4 × (12B UID + DEV_POS)   → 2 + 4*13 = 54
  ```
  解析成 `Layout(vehicle, display, slots)`，结果写进 CCP 日志的同一行：
  `layout_A veh=41 QUAD-2RWD disp=01 DU/DS4 | FL:-- | FR:-- | RL:4314-3837 | RR:…`
- `sendBandwidthRequest()`：
  - 0x00–0xFF 循环；
  - 133B 带宽帧（124B 数据 + 4B 时间/误码率）；
  - 1000ms 超时（与旧 app 一致），连续 8 次失败自动停止；
  - 成功 `success*128*2 / seconds` 计算 B/s。
- `latency` 记录最近 200 次 RX 延迟；`LAT` 条显示 AVG/MAX + sparkline。

### 11.4 布局

```text
Header 32 + 20
ConnectionStrip 34
DeviceRail 28
BT RAW       weight 1.0
CCP PROTOCOL weight 1.15
  action row 22
  bandwidth strip 24
  log weight
  LAT strip 22
```

- RAW input: `HexInput` 24dp + `SEND` / `CYC` / interval。
- CCP buttons: `LAYOUT` / `BANDWIDTH|STOP` / `CLR`。
- `BANDWIDTH` 只在已连接时可用；运行时按钮反白为 `Danger`。
- 所有日志列表自动滚到底部；面板内 `LazyColumn`。
- 顶部 `[NO LINK]` 是 BLE 链路状态，不是 CAN。

### 11.5 关键坑

- `Ble.onRx` 必须保留完整字节；旧代码只存截断字符串，无法做 CCP 拆包。
- CCU 总长是 `len + 5`，不是 `len + 4`；错一位会永远解析不到帧。
- `layout_Q` 的数据必须是 `'R' + 0xFF + 0xFF`；发空数据固件只回当前值、请求语义全丢。
- 扫不到设备的两个坑：① `device.name` 为空的设备以前被直接 `return`，模块名字经常只在 scan response 里；
  ② `BLUETOOTH_SCAN` 没加 `neverForLocation` 时会被当成定位用途，需要额外定位权限。
- 设备条按发现顺序排列（不按 RSSI 重排），否则扫着扫着顺序会跳；连接中 strip 的 `DISC` 就在 `SCAN` 右边，点错就断开。
- 冷启动自动重连：`Ble.autoConnectLast()` 用 SharedPreferences 里的 `last_address`，不需要先扫描；
  失败（模块没上电）安静回到 `NO LINK`，不要停在红色 ERROR。
- 顶栏 `PHY` 用 `readPhy()` / `onPhyUpdate` 的真实值，`MTU` 用 `onMtuChanged` 的值；不要写死 "2M / 247"。
- 掉线重连是一个独立协程循环（`LinkState.Reconnecting(attempt)`），不要用 `connect()` 递归重试：
  - 单次尝试 8s 超时后 `close()`，否则模块没上电时 GATT 会挂 30s；
  - `pendingConnect: CompletableDeferred<Boolean>` 区分"重连尝试"和"手动连接"，回调里先看它；
  - 手动 SCAN / connect / DISC 必须先 `cancelReconnect()`，否则两边抢同一个 GATT；
  - `gatt === opened` 判等，避免上一轮的超时 close 把新一轮的连接关掉。
- 长按复制用 `combinedClickable`，不要用普通 `clickable`。
- 日志行 5.5–6sp 必须配合 `lineHeight = 1.em`。
- 进入页面时不要自动扫描；用户按 `SCAN` 才申请权限/启动扫描。
- 关闭 console 时停扫描、停带宽测试，避免后台残留。

---

## 12. 版本

| 版本 | 内容 |
|---|---|
| v1.0 | CRT 绿、26sp hero、7 模块、紧凑行高 |
| v1.1 | 02 IO、绿色循环按键、居中读数、顶部 context 并入页头 |
| **v1.2** | **03–07 四电机 2×2 三曲线矩阵；隐藏 04/05/06/07 独立面板** |
| **v1.3** | **全局固定页头；00 COCKPIT / 10 TUNE / 20 SCOPE / 30 SYSTEM；调校二级 10.x + 返回箭头** |
| **v1.4** | **全屏 Bluetooth Console：SCAN + BT RAW + CCP PROTOCOL + 带宽/延迟；小字号、长按复制** |
| **v1.5** | **CCP layout 真读（0x04 `'R'` → 0x05 解析 54B）；扫描不再丢无名设备；MTU 512；权限弹窗验证；真机 layout_A + 256/256 带宽帧通过** |
| **v1.6** | **顶栏 PHY/MTU 改真实值（模块实际跑 1M）；设备条按发现顺序、最多 48 个可左右滑；冷启动自动重连上次设备** |
| **v1.7** | **掉线自动重连循环 + `[LINK RETRY]` 橙色闪烁；控制台显示"重连中第 N 次"，DISC 可中止；BT 关→开 12s 内自动连回** |
| **v1.8** | **座舱 EVENT 条改成蓝牙链路事件：平时显示最新一条（连上灰、重连橙、断线红），点击弹出 `LINK LOG` 底部面板（可 CLR）；去掉右侧 `LINK 1M` 和 `SYS OK`，故障仍由 FaultBanner 显示** |
| **v1.9** | **`LINK LOG` 面板高度锁死 8 行（不再随日志增长），超出部分在面板内滑动；行高固定 11dp、单行省略** |
| **v2.0** | **座舱 02 `IO` 接真实数据：readVar 0x1A/0x1B（type=200 `packIU_IO`）4Hz 轮询，静默请求不刷 CCP 日志；控制台打开时暂停轮询；断线清空 IO 并回落 `IU·SIM`** |
| **v2.1** | **01 `DRIVE` 接真实数据：THR/STR 进度条用 `packIU_IO` 的 pu（与 02 IO 同一个数），THR 读数显示百分比、STR 显示 pu；V 用 V_IU；I、P 暂置 0；车速仍为演示值** |
| **v2.2** | **四电机接真实 `packIU_dynFB`（type=201，42B 应答）：流水线请求 + 按 type 认领应答，实测 32–36Hz；IO 4Hz 优先（先等 dyn 排空）；电机卡显示真实 Iq/Ibus/ω/温度/故障** |
| **v2.3** | **Iq/Ibus/ω 不再换 SI，一律按 pu 显示（q1.14 ÷16384，3 位小数）；控制器/电机温度改成 1℃；曲线采样改用帧时间戳当 key，数值不变也连续推进（不再"一卡一卡"）** |
| **v2.4** | **电机曲线改 ±1 双向量程，0 落在中间并画零线；负值（回馈/倒车）不再贴底** |
| **v2.5** | **IO 改流水线（发完不等）+ 每 250ms 给 IO 一个 40ms 独占窗口；GATT 写串行化（单写在飞 + 200ms 看门狗）。结果 dyn 稳定 20Hz、IO 4Hz，没有 400ms 级停顿** |
| **v2.6** | **连上且两种数据都收到后，往 EVENT 日志写一次实测刷新率（`刷新率 dyn 20.0Hz · io 4.0Hz`），只写一次** |
| **v2.7** | **01 DRIVE 车速接真实：取勾选轮子中最低的 ω 换算 km/h；点车速弹「SPEED CALIB」标定框（四轮生效勾选 / ωPU→RPM（提示：有减速器需折算成轮上转速）/ 轮周长 cm / 实时预览），参数存 SharedPreferences** |
| **v2.8** | **03–06 速览窗口可点开 180s 历史弹窗：居中竖光标读数（时间/IQ/IB/ω/温度）、双指缩放 + 单指拖动 + −/＋/全部/最新按钮；历史存内存（20Hz × 3600 帧），速览窗口改为最近约 7s（140 帧）** |
| **v2.9** | **历史弹窗：打开暂停 dyn（EVENT 提示「历史查看中 · 暂停 dyn 读取」）、关闭恢复；弹窗内可多选 FL/FR/RL/RR 纵向排列（顺序 FL→FR→RL→RR，按选几个自动调面板高度）；选择只记内存（进程重启回到点哪个显示哪个）** |
| **v3.0** | **历史弹窗改为"长按 2 秒"打开（避免误触，短按无反应）；横轴恢复按样本下标映射（20Hz 折算秒数），因为打开期间 dyn 与历史都停更，不需要时间戳对齐** |
| **v3.1** | **Y 轴自动分档：±1.0 / ±1.5（有数据 >1.0）/ ±2.0（有数据 >1.5）；座舱四个速览窗口与历史弹窗所有图共用同一档（都取最近 140 帧、四轮全部通道的最大绝对值）** |
| **v3.2** | **Y 轴量程改"记忆式"：升档立即生效，降档要连续 60s 没有高档数据才回落（`MotorAxis.HOLD_MS`）；量程放进全局 `MotorAxis`，座舱四个速览窗口和弹窗共用同一个值，弹窗还用整段历史的最大值兜底避免削平** |
| **v3.3** | **Y 轴量程拆成两份：座舱用全局 `MotorAxis`（7s 窗口 + 60s 记忆降档），历史弹窗自己按整段快照算、打开时定格，互不影响** |
| **v3.4** | **座舱 Y 轴降档记忆时间 60s → 2s；量程心跳由 1s 改为 250ms，尖峰离开窗口后约 2～2.25s 回落（`MotorAxis.HOLD_MS = 2000`）** |
| **v3.5** | **前台保持屏幕常亮：`MainActivity` 加 `FLAG_KEEP_SCREEN_ON`（系统仍可调暗，但不熄屏/不锁屏），避免锁屏/Doze 影响 BLE 连接与 20Hz 轮询** |
| **v3.6** | **Y 轴量程收尾：降档心跳拆成独立 250ms 轮询（原来挂在 `LaunchedEffect(traceMaxAbs)` 里，key 每帧都变、协程不停重启，`while` 里的 delay 永远走不到，数据静止时不降档）；断线时 `MotorAxis.reset()` 回 ±1.0；历史弹窗用 `DisposableEffect` 兜底 `resumeDyn`，避免 Activity 重建后 dyn 永久暂停** |
