package com.pinta.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinta.phone.ble.LinkState
import com.pinta.phone.core.ThrottleParams
import com.pinta.phone.core.pct
import com.pinta.phone.core.trimmed
import com.pinta.phone.ui.components.HairlineH
import com.pinta.phone.ui.components.MetricText
import com.pinta.phone.ui.components.NumberPadSheet
import com.pinta.phone.ui.components.ParamCell
import com.pinta.phone.ui.components.PintaButton
import com.pinta.phone.ui.components.PintaCard
import com.pinta.phone.ui.components.PintaTopBar
import com.pinta.phone.ui.components.SectionLabel
import com.pinta.phone.ui.components.ThrottleCurveChart
import com.pinta.phone.ui.theme.Danger
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.Mint
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Orange
import com.pinta.phone.ui.theme.SurfaceHighest
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---- 参数定义 -------------------------------------------------------------

private enum class ThrottleField(
    val label: String,
    val hint: String,
    val unit: String,
    val decimal: Boolean,
) {
    UV("UV", "欠压阈值 · AD", "AD", false),
    RELEASE("release", "全松开位置 · AD", "AD", false),
    DEPRESS("depress", "全拉下位置 · AD", "AD", false),
    OV("OV", "过压阈值 · AD", "AD", false),
    LOW_DZ("lowDZ", "松开端死区 · AD", "AD", false),
    HIGH_DZ("highDZ", "拉满端死区 · AD", "AD", false),
    TP_X("tpX", "折点横坐标 0.05–0.95", "", true),
    TP_Y("tpY", "折点纵坐标 0.02–1.00", "", true),
}

private fun ThrottleParams.valueOf(field: ThrottleField): Float = when (field) {
    ThrottleField.UV -> uv.toFloat()
    ThrottleField.RELEASE -> release.toFloat()
    ThrottleField.DEPRESS -> depress.toFloat()
    ThrottleField.OV -> ov.toFloat()
    ThrottleField.LOW_DZ -> lowDz.toFloat()
    ThrottleField.HIGH_DZ -> highDz.toFloat()
    ThrottleField.TP_X -> tpX
    ThrottleField.TP_Y -> tpY
}

private fun ThrottleParams.textOf(field: ThrottleField): String {
    val v = valueOf(field)
    return if (field.decimal) v.trimmed(2) else v.roundToInt().toString()
}

/** 返回 null 表示超出允许范围。 */
private fun ThrottleParams.withValue(field: ThrottleField, raw: Float): ThrottleParams? {
    return when (field) {
        ThrottleField.UV -> if (raw in 0f..4094f) copy(uv = raw.roundToInt()) else null
        ThrottleField.RELEASE -> if (raw in 0f..4095f) copy(release = raw.roundToInt()) else null
        ThrottleField.DEPRESS -> if (raw in 0f..4095f) copy(depress = raw.roundToInt()) else null
        ThrottleField.OV -> if (raw in 1f..4095f) copy(ov = raw.roundToInt()) else null
        ThrottleField.LOW_DZ -> if (raw in 0f..4095f) copy(lowDz = raw.roundToInt()) else null
        ThrottleField.HIGH_DZ -> if (raw in 0f..4095f) copy(highDz = raw.roundToInt()) else null
        ThrottleField.TP_X -> if (raw in 0.05f..0.95f) copy(tpX = raw) else null
        ThrottleField.TP_Y -> if (raw in 0.02f..1f) copy(tpY = raw) else null
    }
}

// ---- 传输状态 -------------------------------------------------------------

private sealed interface TransferState {
    data object Idle : TransferState
    data class Busy(val text: String) : TransferState
    data class Done(val text: String, val flashPending: Boolean = false) : TransferState
    data class Failed(val text: String) : TransferState
}

/**
 * 油门标定页。
 * 骨架：实时值 → 曲线 → 参数 → 底部读写。写入分两步：先写内存并校验，再保存 Flash。
 */
@Composable
fun ThrottleScreen(
    link: LinkState,
    onLinkClick: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var read by remember { mutableStateOf(ThrottleParams.Default) }
    var edit by remember { mutableStateOf(ThrottleParams.Default) }
    var input by remember { mutableFloatStateOf(0.42f) }
    var editingField by remember { mutableStateOf<ThrottleField?>(null) }
    var transfer by remember { mutableStateOf<TransferState>(TransferState.Idle) }
    var confirmWrite by remember { mutableStateOf(false) }

    val changes = ThrottleField.values().filter { edit.valueOf(it) != read.valueOf(it) }
    val busy = transfer is TransferState.Busy

    fun simulateRead() {
        if (busy) return
        scope.launch {
            transfer = TransferState.Busy("读取中…")
            delay(650)
            read = edit
            transfer = TransferState.Done("已读取 · 8/8 项")
        }
    }

    fun simulateWrite() {
        confirmWrite = false
        scope.launch {
            for (i in 1..8) {
                transfer = TransferState.Busy("写入中… $i/8")
                delay(110)
            }
            transfer = TransferState.Busy("回读校验…")
            delay(420)
            read = edit
            transfer = TransferState.Done("已写入内存并校验 · 未保存", flashPending = true)
        }
    }

    fun simulateFlashSave() {
        if (busy) return
        scope.launch {
            transfer = TransferState.Busy("保存到 Flash…")
            delay(700)
            transfer = TransferState.Done("已保存 · 校验值 0x3F2A91C4")
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CurveCard(
                params = edit,
                input = input,
                onInputChange = { input = it },
                onParamsChange = { edit = it },
            )

            SectionLabel(
                text = "参数 / REFDATA",
                trailing = {
                    Text(
                        text = if (changes.isEmpty()) "无改动" else "${changes.size} 项待写入",
                        fontSize = 10.sp,
                        color = if (changes.isEmpty()) TextFaint else Orange,
                    )
                },
            )

            ThrottleField.values().toList().chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { field ->
                        ParamCell(
                            label = field.label,
                            value = edit.textOf(field),
                            unit = field.unit,
                            changed = changes.contains(field),
                            modifier = Modifier.weight(1f),
                            onClick = { if (!busy) editingField = field },
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(2.dp))
        }

        ActionBar(
            transfer = transfer,
            changeCount = changes.size,
            busy = busy,
            onRead = { simulateRead() },
            onWrite = { confirmWrite = true },
            onSaveFlash = { simulateFlashSave() },
        )
    }

    editingField?.let { field ->
        NumberPadSheet(
            title = "编辑 · ${field.label}",
            initial = edit.textOf(field),
            unit = field.unit.ifBlank { null },
            allowDecimal = field.decimal,
            rangeHint = field.hint,
            onDismiss = { editingField = null },
            onConfirm = { text ->
                editingField = null
                val parsed = text.toFloatOrNull()
                if (parsed == null) {
                    transfer = TransferState.Failed("输入无效：$text")
                } else {
                    val next = edit.withValue(field, parsed)
                    if (next == null) {
                        transfer = TransferState.Failed("${field.label} 超出范围：$text")
                    } else {
                        edit = next
                    }
                }
            },
        )
    }

    if (confirmWrite) {
        WriteConfirmDialog(
            changes = changes,
            read = read,
            edit = edit,
            onDismiss = { confirmWrite = false },
            onConfirm = { simulateWrite() },
        )
    }
}

@Composable
private fun CurveCard(
    params: ThrottleParams,
    input: Float,
    onInputChange: (Float) -> Unit,
    onParamsChange: (ThrottleParams) -> Unit,
) {
    val ad = params.adMin + input * params.adRange
    val output = params.curve(input)
    val fault = params.isFault(ad)

    // 曲线和输入滑条放在同一张卡里：滑条就是曲线的输入，
    // 分成两张卡在 360×780dp 的屏幕上会把参数区挤没。
    PintaCard(padding = 12.dp) {
        SectionLabel(
            text = "响应曲线",
            trailing = {
                Text(
                    text = "拖折点改形状",
                    fontSize = 9.sp,
                    color = TextFaint,
                )
            },
        )
        Spacer(Modifier.height(6.dp))
        ThrottleCurveChart(
            params = params,
            input = input,
            onInputChange = onInputChange,
            onParamsChange = onParamsChange,
            modifier = Modifier.fillMaxWidth().height(148.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            MetricText(
                value = output.pct(),
                unit = "输出",
                size = 24.sp,
                color = if (fault) Danger else Orange,
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "AD ${ad.roundToInt()}",
                    fontFamily = Mono,
                    fontSize = 10.sp,
                    color = TextDim,
                )
                if (fault) {
                    Text(text = "越界", fontSize = 9.sp, color = Danger)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        ThinSlider(value = input, onValueChange = onInputChange)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("松开 ${params.adMin}", fontFamily = Mono, fontSize = 9.sp, color = TextFaint)
            Text("拉满 ${params.adMax}", fontFamily = Mono, fontSize = 9.sp, color = TextFaint)
        }
    }
}

@Composable
private fun ThinSlider(value: Float, onValueChange: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val thumbX = size.width * value.coerceIn(0f, 1f)
            drawLine(
                color = SurfaceHighest,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Orange,
                start = Offset(0f, cy),
                end = Offset(thumbX, cy),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = TextPrimary,
                start = Offset(thumbX, cy - 10.dp.toPx()),
                end = Offset(thumbX, cy + 10.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ActionBar(
    transfer: TransferState,
    changeCount: Int,
    busy: Boolean,
    onRead: () -> Unit,
    onWrite: () -> Unit,
    onSaveFlash: () -> Unit,
) {
    val (dot, text) = when (transfer) {
        TransferState.Idle -> TextFaint to "空闲 · 先读取再编辑"
        is TransferState.Busy -> Orange to transfer.text
        is TransferState.Done -> if (transfer.flashPending) Orange to transfer.text else Mint to transfer.text
        is TransferState.Failed -> Danger to transfer.text
    }

    Column(modifier = Modifier.fillMaxWidth().background(SurfaceLow)) {
        HairlineH()
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(dot, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(text = text, fontSize = 11.sp, color = TextDim, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (transfer is TransferState.Done && transfer.flashPending) {
                    Text(
                        text = "保存到 Flash",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Orange,
                        modifier = Modifier
                            .background(Orange.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .pointerInput(Unit) { detectTapGestures { onSaveFlash() } },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PintaButton(
                    label = "读取",
                    primary = false,
                    enabled = !busy,
                    modifier = Modifier.weight(0.36f),
                    onClick = onRead,
                )
                PintaButton(
                    label = if (changeCount == 0) "写入" else "写入 $changeCount 项",
                    enabled = !busy && changeCount > 0,
                    modifier = Modifier.weight(0.64f),
                    onClick = onWrite,
                )
            }
        }
    }
}

@Composable
private fun WriteConfirmDialog(
    changes: List<ThrottleField>,
    read: ThrottleParams,
    edit: ThrottleParams,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLow,
        titleContentColor = TextPrimary,
        textContentColor = TextDim,
        title = { Text("写入 ${changes.size} 项参数", fontSize = 16.sp) },
        text = {
            Column {
                changes.forEach { field ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = field.label,
                            fontSize = 12.sp,
                            color = TextDim,
                            modifier = Modifier.width(62.dp),
                        )
                        Text(
                            text = read.textOf(field),
                            fontFamily = Mono,
                            fontSize = 12.sp,
                            color = TextFaint,
                        )
                        Text(text = "  →  ", fontSize = 12.sp, color = TextFaint)
                        Text(
                            text = edit.textOf(field),
                            fontFamily = Mono,
                            fontSize = 12.sp,
                            color = Orange,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "写入后会自动回读校验；保存到 Flash 是单独一步。",
                    fontSize = 10.sp,
                    color = TextFaint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("写入", color = Orange, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextDim)
            }
        },
    )
}
