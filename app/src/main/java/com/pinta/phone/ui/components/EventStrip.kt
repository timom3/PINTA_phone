package com.pinta.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinta.phone.ui.theme.Amber
import com.pinta.phone.ui.theme.Danger
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Phosphor
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint

/** 日志面板的行高/最大可见行数：内容少时不高，内容多时固定 8 行 + 内部滚动。 */
private val LogRowHeight = 11.dp
private const val LogMaxVisibleRows = 8
private val LogRowGap = 3.dp
private val LogListPadding = 6.dp
private val LogListMaxHeight: Dp =
    LogRowHeight * LogMaxVisibleRows + LogRowGap * (LogMaxVisibleRows - 1) + LogListPadding * 2

/**
 * 底部 EVENT 条：显示最新一条，点开是该页的完整日志。
 *
 * 座舱传 Ble.log（链路事件），其余页面传 [com.pinta.phone.core.UiLog] 对应通道。
 */
@Composable
fun EventStrip(
    logs: List<String>,
    sheetTitle: String = "LOG",
    emptyText: String = "NO EVENT",
    latestColor: Color = TextFaint,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showLog by remember { mutableStateOf(false) }
    val latest = logs.lastOrNull()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .clickable { showLog = true }
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("EVENT", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
        Spacer(Modifier.width(6.dp))
        Text(
            text = latest ?: emptyText,
            fontFamily = Mono,
            fontSize = 8.sp,
            color = latestColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }

    if (showLog) {
        LogSheet(
            title = sheetTitle,
            logs = logs,
            emptyText = emptyText,
            onClear = onClear,
            onDismiss = { showLog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSheet(
    title: String,
    logs: List<String>,
    emptyText: String,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            // 等一帧让 maxValue 反映最新高度，再贴到底（新日志在下方）
            withFrameNanos { }
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(36.dp, 3.dp).background(Hairline))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp)
                .padding(bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    color = Phosphor,
                )
                Spacer(Modifier.width(6.dp))
                Text("${logs.size}", fontFamily = Mono, fontSize = 8.sp, color = TextFaint)
                Spacer(Modifier.weight(1f))
                if (onClear != null) {
                    Text(
                        text = "CLR",
                        fontFamily = Mono,
                        fontSize = 9.sp,
                        color = TextDim,
                        modifier = Modifier.clickable(onClick = onClear).padding(horizontal = 8.dp),
                    )
                }
                Text(
                    text = "CLOSE",
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = TextDim,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LogListMaxHeight)
                    .background(SurfaceLow.copy(alpha = 0.2f))
                    .border(1.dp, Hairline),
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = emptyText,
                        fontFamily = Mono,
                        fontSize = 8.sp,
                        color = TextFaint,
                        modifier = Modifier.align(Alignment.Center).padding(vertical = 24.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(LogListPadding),
                        verticalArrangement = Arrangement.spacedBy(LogRowGap),
                    ) {
                        logs.forEach { line ->
                            Text(
                                text = line,
                                fontFamily = Mono,
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                color = eventLineColor(line),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.height(LogRowHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 按关键字给日志行上色：失败红、重连橙、就绪绿，其余灰。 */
fun eventLineColor(line: String): Color = when {
    line.contains("断开") || line.contains("失败") ||
        line.contains("未成功") || line.contains("中断") -> Danger
    line.contains("重连") || line.contains("暂停") -> Amber
    line.contains("服务就绪") || line.contains("MTU=") || line.contains("恢复") -> Phosphor
    else -> TextDim
}
