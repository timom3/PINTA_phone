package com.pinta.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinta.phone.ble.LinkState
import com.pinta.phone.ui.components.PintaCard
import com.pinta.phone.ui.components.PintaTopBar
import com.pinta.phone.ui.components.SectionLabel
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mint
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary

const val ROUTE_LAYOUT = "layout"
const val ROUTE_THROTTLE = "throttle"
const val ROUTE_STEERING = "steering"
const val ROUTE_BATTERY = "battery"
const val ROUTE_NODES = "nodes"

private data class TuneEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val status: String,
    val statusColor: Color,
)

private val tuneEntries = listOf(
    TuneEntry(
        key = ROUTE_LAYOUT,
        title = "整车布局",
        subtitle = "车型 · 仪表类型 · 控制器 UID 与对中",
        status = "4/4 已绑定",
        statusColor = Mint,
    ),
    TuneEntry(
        key = ROUTE_THROTTLE,
        title = "油门曲线",
        subtitle = "8 项 refData · 死区与两段折线",
        status = "已读取",
        statusColor = Mint,
    ),
    TuneEntry(
        key = ROUTE_STEERING,
        title = "转向与差速",
        subtitle = "9 项 refData · 死区 / 滤波 / 差速衰减",
        status = "已读取",
        statusColor = Mint,
    ),
    TuneEntry(
        key = ROUTE_BATTERY,
        title = "电池与保护",
        subtitle = "4 轮 × UV / LV / OV / 母线电流 / 相电流",
        status = "未读取",
        statusColor = TextFaint,
    ),
    TuneEntry(
        key = ROUTE_NODES,
        title = "控制器与固件",
        subtitle = "节点版本 · 错误码 · 变量档案",
        status = "5 节点在线",
        statusColor = Mint,
    ),
)

/**
 * 调校入口：把原来平铺的四个页收成一张清单，
 * 每一行只说清"是什么、当前什么状态"。
 */
@Composable
fun TuneScreen(
    link: LinkState,
    onLinkClick: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tuneEntries.forEach { entry ->
                PintaCard(padding = 0.dp, onClick = { onOpen(entry.key) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = entry.subtitle,
                                fontSize = 11.sp,
                                color = TextDim,
                            )
                        }
                        Text(
                            text = entry.status,
                            fontSize = 10.sp,
                            color = entry.statusColor,
                        )
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextFaint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            WriteFlowNote()
        }
    }
}

/** 新增的行为说明：写入不是一按就完事。 */
@Composable
private fun WriteFlowNote() {
    PintaCard(padding = 14.dp) {
        SectionLabel("写入流程")
        Spacer(Modifier.height(10.dp))
        listOf(
            "1" to "读取：拉一遍当前值，作为对比基准",
            "2" to "编辑：只标记改动的项",
            "3" to "写入：先弹差异确认，再逐项回读校验",
            "4" to "保存：确认后写 Flash，取出校验值比对",
        ).forEach { (index, text) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = index,
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    color = Mint,
                    modifier = Modifier.width(18.dp),
                )
                Text(text = text, fontSize = 11.sp, color = TextDim)
            }
        }
    }
}
