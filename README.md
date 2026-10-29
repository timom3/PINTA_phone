# PINTA Phone — 原型

安卓手机版的 IUPro 客户端：**驾驶监控 + 参数标定**，深色工业风，默认中文。
目标机型：Samsung Galaxy S24（SM-S9210，1080×2340，**实测逻辑尺寸 360×780 dp**，密度 480），竖屏单手。

这是用来验证 UI 手感的第一版原型：界面是真的、曲线算法是真的、蓝牙链路是真的，
**数据是模拟的**（还没接 CCU 协议层）。

> 已在真机 SM-S9210 上安装验证（`adb install` 通过，四个 tab 全部可交互）。
> 真机比设计基准窄 24dp / 矮 52dp，第一版把四轮输出卡的最后两行裁掉了，
> 已改为「四行按权重平分 + 卡片高度自适应」，现在 360×780 也能完整显示。
> 座舱页含固定全局页头、`IO` 与四电机 2×2 三曲线矩阵；蓝牙控制台含扫描 + `BT RAW` + `CCP PROTOCOL` + 带宽/延迟。
> 设计基线图见 [p1-cockpit-dashboard.jpg](docs/images/p1-cockpit-dashboard.jpg)、
> [p1-link-console.jpg](docs/images/p1-link-console.jpg)；调试过程截图（`shots/`）属本地中间产物，不入库。
> 设计基线见 [P1_COCKPIT_STYLEGUIDE.md](docs/P1_COCKPIT_STYLEGUIDE.md)，实现复现见 [P1_COCKPIT_IMPLEMENTATION.md](docs/P1_COCKPIT_IMPLEMENTATION.md)；
> 机器可读 token 见 [p1-cockpit-tokens.json](docs/p1-cockpit-tokens.json)；设计 brief 见 [CRT_GREEN_UI_BRIEF.md](docs/CRT_GREEN_UI_BRIEF.md)；
> 版本记录见 [CHANGELOG.md](CHANGELOG.md)（当前 `0.1.1`，座舱速览量程自动适配 + 180s 历史 + 屏幕常亮）。

## 现在能跑什么

| 模块 | 状态 | 说明 |
|---|---|---|
| 座舱 | 部分真实 | **01 `DRIVE`：THR 进度条按 pu 驱动、读数显示百分比，STR 进度条与读数都是 pu，V 取 V_IU；车速来自 dyn_fb（勾选轮子里最慢的一个），点车速数字弹出「SPEED CALIB」标定框**；I 与 P 仍显示 0；**02 `IO` 全量真实**（readVar 0x1A/0x1B，type=200，4Hz）：档位/REV/FIX/P/L/R/BRK、转把与转向 pu、NTC、2.0REF、V_IU/V_ACC/V_12；**四电机卡真实**（type=201 `packIU_dynFB`，≈20Hz）：Iq/Ibus/ω 一律按 **pu（q1.14/16384，3 位小数）**显示、速览曲线 **双向量程（中间零线），量程按数据自动分档 ±1.0/±1.5/±2.0、升档立即、降档要 2s 无高峰数据，弹窗量程独立计算**、按帧时间戳连续推进（最近约 7s），控制器/电机温度按 1℃ 显示、故障码；**长按 03–06 任一卡片 2 秒弹出 180s 历史**（弹窗内可多选 FL/FR/RL/RR 纵向排列、居中光标读数、双指缩放、左右拖动；**打开时暂停 dyn 轮询、关闭恢复**，EVENT 有提示；数据只在内存，选择只记在当前进程）。EVENT 条显示最新蓝牙事件，点开是链路日志 |
| 调校入口 | 可交互 | 5 个模块清单（布局 / 油门 / 转向差速 / 电池 / 控制器） |
| 油门曲线 | 可交互 | 与固件 `L1_THR_curve()` 一致的两段折线；拖动折点改 tpX/tpY；模拟输入滑条；8 项参数；大号数字键盘；读取 / 写入 / 保存 Flash 三段式状态机（模拟） |
| 监控 | 静态骨架 | 8 通道选择、示波器网格、采样设置（未接 ACQ） |
| 设置 | 静态骨架 | 车辆参数（轮径/减速比）、写入安全、关于 |
| 蓝牙 | 真实 | 全屏控制台：扫描/连接、冷启动+掉线自动重连（顶栏 `[LINK]` 橙色闪烁）、真实 MTU/PHY 显示、`BT RAW` 收发、`CCP PROTOCOL` 帧日志、layout 读写（0x04/0x05，解析车型/仪表/四轮 UID+位置）、带宽/延迟测试、长按复制 |

## 架构

```
app/src/main/java/com/pinta/phone/
├─ MainActivity.kt          入口，edge-to-edge
├─ ble/Ble.kt               链路层：XY-MBD22A（FFE1/FFE2/FFE3）、扫描/连接/MTU/通知
├─ ble/Ccu.kt               协议层：0x48/0x54 组帧、Sum8、拆包、layout / bandwidth
├─ core/
│  ├─ ThrottleParams.kt     油门参数 + 曲线算法（照抄固件）
│  ├─ Telemetry.kt          一帧实时数据 + 演示数据源
│  └─ Format.kt             数值格式化
└─ ui/
   ├─ PintaApp.kt           4 个 tab + 蓝牙面板
   ├─ LinkConsole.kt        全屏蓝牙控制台（SCAN / BT RAW / CCP）
   ├─ DashScreen.kt         座舱（固定密度布局，一屏放下）
   ├─ TuneScreen.kt         调校入口
   ├─ ThrottleScreen.kt     油门曲线
   ├─ Placeholder.kt        监控 / 设置 / 未实现模块
   ├─ components/           TopBar / 卡片 / 四轮条 / 迷你曲线 / 曲线图 / 键盘
   └─ theme/Theme.kt        色阶、字号、等宽数字
```

设计约束（来自 `kinetic_grid`，但重排了信息架构）：

- 色阶分层代替分割线；只有 8% 的白发丝边
- 0 圆角、无阴影、无弹性动画
- 数值一律等宽字体，避免跳动时布局抖动
- 橙色只给"可操作 / 正在变化"，静态标题是灰的
- 调校页固定底部操作区，拇指可达；正文 13sp 起，不再是平板时代的 8sp

## 构建

```powershell
$env:GRADLE_USER_HOME='D:\codex\PINTA_phone\.gradle-home'
$env:GRADLE_RO_DEP_CACHE='C:\Users\wdach\.gradle\caches'
.\gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 14 MB）

> 两个环境变量是给 DSH 沙箱用的：Gradle 默认写 `~/.gradle`（只读），所以 home 放进工程内；
> 依赖缓存挂宿主机已有的一份，避免重新下载。普通开发机上不需要这两个变量。

## 安装到手机

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

或者把 APK 拷进手机点击安装（需允许"未知来源"）。首次打开点右上角「未连接」可扫设备。

## 与旧平板 app 的差别

| | 旧版 | 新版 |
|---|---|---|
| 导航 | 5 页横滑 + 弹窗 | 4 tab + 调校二级页 |
| 日志 | 每页一个大日志框 | 收进链路面板，页面只留一行状态 |
| 写入 | 每个页面各写一套，行为不一致 | 统一：读取 → diff 确认 → 写入 → 回读校验 → 保存 Flash |
| 参数地址 | 依赖手工 `register_IU` 文本 + 正则 | 计划改为设备 vReg 档案自动读取 |
| 字号 | 8sp（平板遗留） | 正文 13sp，数值 17–42sp |
| 车速 | 无 | omega_pu + 轮径/减速比换算 |

## 已知限制

- 所有实时数据来自 `rememberDemoTelemetry()`，不是真车数据 —— 除了座舱 01 `DRIVE`（THR/STR/V）、02 `IO`
  和四电机卡：它们走真实 `readVar`（type=200 IO 4Hz / type=201 dyn_fb），控制台打开时暂停轮询；前台保持屏幕常亮（不熄屏、不锁屏）
- dyn_fb 与 IO 4Hz 共存时稳定跑 **20Hz**（实测 19.9–20.9，IO 3.8–4.2）：模块/固件总共约 30 应答/s，
  20+4 留余量才不丢包；只跑 dyn 时能到 32–36Hz，但混着发会互相丢。想更快就让固件按 N 帧打包回
- 轮询节奏在 `Ccu.kt`：`IU_DYN_PERIOD_MS = 40`（dyn 20Hz）、IO 每 250ms 一个 40ms 独占窗口；
  改 `IU_DYN_PERIOD_MS = 33` 可到 dyn ~24Hz，代价是 IO 掉到 ~3.3Hz
- 每次连上、两种数据都收到后，EVENT 日志里会写**一次**实测刷新率（`刷新率 dyn 20.0Hz · io 4.0Hz`），之后不再刷新
- dyn_fb 里的 `tptCtrl` / `tptMotor` 现在是 0，电机卡温度因此显示 `-50℃`（控制器没回数据或固件没填）
- 固件侧还有两个坑（见下）导致 `V_ACC`/`V_12`/`NTC` 目前不对，app 解析按文档布局，固件修好后自动正确
- packIU_IO 的固件侧还没补齐：`DPD.L2.UOP.IO.temperature_C` 全工程没有赋值（NTC 恒 `-50℃`）；
  ACC 槽写的还是 `VbusIU_01V`（还没有真正的 ACC 电压源）
- V_12 固件按 **0.01V** 精度打包（`1219` = 12.19V，实测吻合），app 里除 100 后仍按 0.1V 显示（`12.2V`）；
  V_IU / V_ACC 仍是 0.1V
- CCU 协议层只接了诊断子集：`layout`（0x04/0x05）与 `bandwidth`（0x00/0x01）已在真机（XY_BLE_1152V232）验证；
  vReg（0x10/0x13）、read/write var（0x1A/0x1C）、ACQ（0x20–0x25）尚未移植
- 顶栏 `PHY` / `MTU` 是 `readPhy()` / `onMtuChanged` 读回的真实值；这块模块实际跑 **1M PHY**（请求 2M 没生效），
  MTU 512。带宽实测 ~5.1 KB/s，瓶颈是逐帧一问一答的往返（~46ms/帧），不是 PHY 上限
- 设备条按**发现顺序**排列（不按 RSSI 重排），再多设备也只能左右滑；最多保留 48 个
- 掉线重连：模块掉电/超时后先红闪 1.2s `[LINK LOST]`，再转橙色闪烁 `[LINK RETRY]` 一直重试
  （单次尝试 8s 超时，间隔 1s → 2s → 4s 封顶）；用户点 SCAN / 连别的设备 / 点 DISC 就停下重连，交回手动
- 重连循环在后台跑，没有上限；一直不连会一直试（耗电可控但不会自己停）
- vReg 设备档案、ACQ 采样引擎尚未接入
- OTA：固件目前没有升级命令，本轮不做
- 曲线页参数区需要滚动才能看全 8 项（有意取舍：曲线是主体）

## 下一步

1. **协议层（诊断子集已完成）**：0x48/0x54 组帧、Sum8、跨通知拆包、请求串行 + 1s 超时；layout / bandwidth 已真机通过。
   待补 vReg（0x10–0x13）、read/write var（0x1A–0x1C）、ACQ（0x20–0x25）
2. **设备档案**：0x10/0x11 + 0x12/0x13 读回 111 个 var / 44 个 refData，解析 `name[19]+type+addr(+min/max)`，缓存到本地
3. **实时数据**：ACQ（8 通道、mode C、1–255 ms）驱动仪表与监控页
4. **其余调校页**：布局、电池保护、转向差速
5. **数据记录**：示波器录制 + CSV 导出

## 参考

- 固件：`D:\codex\IUPro\firmware\IUPro_Q003_S_V2`（协议见 `src/L1_midLayer/comm/L1_CCU.h`，参数表见 `src/LA_DPD/LA_varRegister.c`）
- 旧 app：`D:\codex\android_PINTA_3`
- 设计参考：`D:\codex\归档\PINTA_phone_1\UI_design\kinetic_grid\DESIGN.md`
