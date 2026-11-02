# 更新日志

版本号对应 `app/build.gradle.kts` 里的 `versionCode` / `versionName`。
座舱逐版实现细节（v1.0–v3.x 每一版改了什么）另见
[docs/P1_COCKPIT_IMPLEMENTATION.md](docs/P1_COCKPIT_IMPLEMENTATION.md) 末尾的版本表。

## [0.1.2] — 2026-11-02

EVENT 条从座舱页的私有实现抽成通用组件，四个页面都能看到各自的日志。`versionCode 2 → 3`。

### 新增

- **`ui/components/EventStrip.kt`**：底部 EVENT 条 + 点开的 `LogSheet`（8 行定高、内部滚动、
  新日志自动贴底、`CLR` 清空）。座舱传 `Ble.log` 显示链路事件，其余页面传 `UiLog` 的通道
- **`core/UiLog.kt`**：三条独立日志通道 `Tune` / `Scope` / `Setup`（`LogChannel` 枚举自带
  面板标题与空态文案），各留 120 行，格式与链路日志一致 `HH:mm:ss.SSS  文本`
- 调校：进入调校、打开模块、读取/写入/保存 Flash 都写 `TUNE LOG`
- 监控：8 通道从"前两个高亮"改成真正可勾选，勾选/取消写 `SCOPE LOG`
- 设置：车辆参数改读 `SpeedSettings` 真实标定值（`ωPU → RPM`、轮周长），进入页面写 `SYSTEM LOG`

### 变更

- **离开座舱页自动暂停 IO/dyn 轮询**（`Ccu.setPollingEnabled`），回到座舱恢复，并在链路日志
  记「离开座舱 · 暂停 IO/dyn 读取」/「回到座舱 · 恢复 IO/dyn 读取」。
  原来只有打开蓝牙控制台才暂停，现在统一成 `tab == Dash && !showLink`
- `EventStrip` 的着色函数 `eventLineColor` 比原来的 `logLineColor` 多了「暂停」→ 橙、「恢复」→ 绿，
  新页面的日志也能正确上色

### 修复

- **切页后实测刷新率会重复上报**：`rateReported = false` 原来写在 `startIoPolling()` 里，
  每次离开再回到座舱都会重置、重新写一条「刷新率 dyn …」。现在改由 `clearIo()` 在断线时复位，
  一次连接只报一次

### 文档

- `docs/P1_COCKPIT_IMPLEMENTATION.md` 补 v3.7，并修掉新加的那行误用了 v3.6 编号（表里出现两条 v3.6）
- README 状态表同步监控/设置的现状

## [0.1.1] — 2026-10-29

座舱 03–06 四个速览窗口的曲线量程、历史回看和屏幕常亮。`versionCode 1 → 2`。

### 新增

- **Y 轴量程自动适配**（`core/MotorAxis.kt`、`core/MotorHistory.kt`）
  按 12 条曲线（4 轮 × IQ/IB/ω）的绝对值分 **±1.0 / ±1.5（有数据 >1.0）/ ±2.0（有数据 >1.5）**
  三档。**升档立即生效**（看到尖峰马上放大），**降档要等 2s 没有高档数据**（`MotorAxis.HOLD_MS`），
  避免尖峰滑出窗口后量程来回跳。座舱四张图共用一个档位。
- **长按 03–06 卡片 2 秒打开 180s 历史弹窗**（`ui/MotorHistoryDialog.kt`）
  - 历史存在内存里：`MotorHistory` 环形缓冲 **20Hz × 3600 帧 ≈ 180s**，app 退出即消失
  - 弹窗内居中竖光标读数：时刻（`HH:mm:ss.SSS`）、相对最新的秒差、IQ/IB/ω、控制器/电机温度
  - **可多选 FL/FR/RL/RR**，按 FL→FR→RL→RR 从上到下纵向排列，选几个自动调面板高度
  - 双指缩放（20 帧～全部）、单指左右拖动，另有 `−` / `＋` / `全部` / `最新` 按钮
  - 量程独立：按整段快照的最大值算一次、打开即定格，和座舱互不影响
  - 选择只记在当前进程（`HistorySelection`），重启回到"点哪个显示哪个"
- **前台屏幕常亮**（`MainActivity` 加 `FLAG_KEEP_SCREEN_ON`）
  不熄屏、不锁屏；系统仍会调暗。避免锁屏 / Doze 掐断 BLE 连接和 20Hz 轮询。

### 变更

- 座舱速览窗口从最近 80 帧改为最近约 **7s（140 帧）**，长窗口交给历史弹窗
- `MotorCurves` / `HistoryChart` 的量程参数化，不再写死 ±1
- 打开历史弹窗时暂停 dyn 轮询（`Ccu.pauseDyn()`，EVENT 写「历史查看中 · 暂停 dyn 读取」），
  关闭时恢复并写「关闭历史 · 恢复 dyn 读取」——弹窗里的数据在打开那一刻定格

### 修复

- 降档心跳实际上从未运行：原来把 `while (true) { delay(250) }` 挂在
  `LaunchedEffect(traceMaxAbs)` 里，而 `traceMaxAbs` 每帧都在变、协程不停重启，
  `delay` 永远等不到。现在拆成"新数据立即判档" + "独立 250ms 心跳"两段
- 断线后量程停在最后一档不回落：`MotorAxis.reset()` 之前没有任何调用点，
  现在 `link` 一离开 `Connected` 就回 ±1.0
- 历史弹窗打开期间 Activity 被系统回收重建时，`onDismiss` 不会执行，
  `dynPaused` 会永久卡在 true，dyn 再也不轮询。改用 `DisposableEffect` 兜底 `resumeDyn()`

### 文档

- 新增本文件
- `docs/P1_COCKPIT_IMPLEMENTATION.md`：补 v2.8–v3.6 版本记录，并修掉 v2.9 / v3.0 之间
  把表格截断的空行
- 设计基线图入库：`docs/images/p1-cockpit-dashboard.jpg`、`p1-link-console.jpg`（共 683 KB，
  JPEG）；调试过程截图 `shots/` 属中间产物，不入库（`.gitignore`）

## [0.1.0] — 2026-10-29

首个提交，`versionCode 1`。真机机型 Samsung Galaxy S24（SM-S9210，360×780dp，竖屏单手）。

- Gradle 8.2 + Kotlin + Jetpack Compose 单模块工程（`app`），`minSdk 26 / targetSdk 34`
- 座舱页：01 `DRIVE`、02 `IO`、03–06 四电机 2×2 三曲线矩阵、EVENT 链路条
- BLE 链路（`ble/Ble.kt`）+ CCU 协议层（`ble/Ccu.kt`）：扫描 / 自动重连 / MTU / PHY、带宽与延迟测试
- 真实数据：IO 走 `readVar 0x1A/0x1B`（type=200，4Hz），四电机走 `packIU_dynFB`（type=201，20Hz），
  Iq/Ibus/ω 一律 pu（q1.14 ÷ 16384，3 位小数）
- 车速标定：dyn_fb 的 ω → km/h，四轮生效勾选 / ωPU→RPM / 轮周长，存 SharedPreferences
- 调校、监控、系统三页为可交互骨架
- 设计基线与实现复现文档：`docs/CRT_GREEN_UI_BRIEF.md`、`docs/P1_COCKPIT_STYLEGUIDE.md`、
  `docs/P1_COCKPIT_IMPLEMENTATION.md`、`docs/p1-cockpit-tokens.json`
