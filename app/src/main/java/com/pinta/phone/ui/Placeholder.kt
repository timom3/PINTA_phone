package com.pinta.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinta.phone.ble.LinkState
import com.pinta.phone.ui.components.HairlineH
import com.pinta.phone.ui.components.PintaCard
import com.pinta.phone.ui.components.PintaTopBar
import com.pinta.phone.ui.components.SectionLabel
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.SurfaceContainer
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary

/** 监控页：先把 8 通道示波器的骨架摆出来，ACQ 引擎下一阶段接入。 */
@Composable
fun ScopeScreen(link: LinkState, onLinkClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PintaCard(padding = 14.dp) {
                SectionLabel("通道选择")
                Spacer(Modifier.height(10.dp))
                listOf(listOf("CH1", "CH2", "CH3", "CH4"), listOf("CH5", "CH6", "CH7", "CH8"))
                    .forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(30.dp)
                                        .background(
                                            if (ch == "CH1" || ch == "CH2") SurfaceContainer else SurfaceLow,
                                        )
                                        .border(1.dp, Hairline),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = ch,
                                        fontFamily = Mono,
                                        fontSize = 11.sp,
                                        color = if (ch == "CH1" || ch == "CH2") TextPrimary else TextFaint,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
            }

            PintaCard(padding = 0.dp) {
                Box(modifier = Modifier.fillMaxWidth().height(230.dp).background(SurfaceContainer)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val grid = Color(0x10FFFFFF)
                        for (i in 0..6) {
                            val x = size.width * i / 6f
                            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                        }
                        for (i in 0..5) {
                            val y = size.height * i / 5f
                            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                        }
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "采样引擎未接入",
                            fontSize = 12.sp,
                            color = TextDim,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "ACQ_set → ACQ_start → ACQ_getData",
                            fontFamily = Mono,
                            fontSize = 9.sp,
                            color = TextFaint,
                        )
                    }
                }
            }

            PintaCard(padding = 14.dp) {
                SectionLabel("采样设置")
                Spacer(Modifier.height(10.dp))
                SettingRow("模式", "C · 连续")
                SettingRow("间隔", "50 ms")
                SettingRow("记录", "未开始")
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 12.sp, color = TextDim, modifier = Modifier.weight(1f))
        Text(text = value, fontFamily = Mono, fontSize = 12.sp, color = TextPrimary)
    }
}

/** 设置页：结构先定下来，具体能力后续补。 */
@Composable
fun SetupScreen(link: LinkState, onLinkClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PintaCard(padding = 14.dp) {
                SectionLabel("设备")
                Spacer(Modifier.height(8.dp))
                SettingRow("自动连接", "上次设备")
                SettingRow("设备档案", "v1.260406.3")
                SettingRow("节点", "IU + 4 控制器")
            }
            PintaCard(padding = 14.dp) {
                SectionLabel("车辆参数")
                Spacer(Modifier.height(8.dp))
                SettingRow("轮径", "— mm")
                SettingRow("减速比", "—")
                SettingRow("车速来源", "omega_pu 换算")
            }
            PintaCard(padding = 14.dp) {
                SectionLabel("写入安全")
                Spacer(Modifier.height(8.dp))
                SettingRow("写入前二次确认", "开启")
                SettingRow("静止联锁", "开启")
                SettingRow("写入后自动校验", "开启")
            }
            PintaCard(padding = 14.dp) {
                SectionLabel("关于")
                Spacer(Modifier.height(8.dp))
                SettingRow("版本", "0.1.0 原型")
                SettingRow("协议", "CCU 0x48 · Sum8")
                SettingRow("固件 OTA", "暂不支持")
            }
        }
    }
}

/** 调校里尚未实现的模块，用同一套视觉说话。 */
@Composable
fun TuneDetailPlaceholder(title: String, onBack: () -> Unit, link: LinkState, onLinkClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PintaCard(padding = 16.dp) {
                Text(
                    text = "这个模块的交互骨架已经定好：",
                    fontSize = 13.sp,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(10.dp))
                listOf(
                    "顶部实时值 → 中部可视化 → 下部参数 → 底部读写",
                    "读取后与设备值做差异高亮",
                    "写入后回读校验，再单独一步保存到 Flash",
                ).forEach {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text("· ", fontSize = 12.sp, color = TextFaint)
                        Text(it, fontSize = 12.sp, color = TextDim)
                    }
                }
            }
            HairlineH()
            Text(
                text = "原型只实现了仪表、调校入口和油门曲线三屏。",
                fontSize = 11.sp,
                color = TextFaint,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
