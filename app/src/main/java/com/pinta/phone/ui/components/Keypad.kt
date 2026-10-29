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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinta.phone.ui.theme.Hairline
import com.pinta.phone.ui.theme.Mono
import com.pinta.phone.ui.theme.Orange
import com.pinta.phone.ui.theme.SurfaceContainer
import com.pinta.phone.ui.theme.SurfaceHighest
import com.pinta.phone.ui.theme.SurfaceLow
import com.pinta.phone.ui.theme.TextDim
import com.pinta.phone.ui.theme.TextFaint
import com.pinta.phone.ui.theme.TextPrimary

/**
 * 大号数字键盘。工位上戴手套也能点，所以键高 58dp。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPadSheet(
    title: String,
    initial: String,
    unit: String?,
    allowDecimal: Boolean,
    rangeHint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initial) }

    fun press(key: String) {
        when (key) {
            "." -> if (allowDecimal && !text.contains(".")) {
                text = if (text.isEmpty()) "0." else "$text."
            }

            "⌫" -> if (text.isNotEmpty()) text = text.dropLast(1)
            else -> {
                if (text == "0") text = key else text += key
                if (text.length > 7) text = text.take(7)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(36.dp, 3.dp).background(Hairline))
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = title, fontSize = 12.sp, letterSpacing = 1.sp, color = TextDim)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (text.isEmpty()) "0" else text,
                    fontFamily = Mono,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Start,
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        fontFamily = Mono,
                        fontSize = 13.sp,
                        color = TextDim,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                    )
                }
            }
            if (rangeHint != null) {
                Text(text = rangeHint, fontSize = 10.sp, color = TextFaint)
            }

            val lastRow = if (allowDecimal) listOf(".", "0", "⌫") else listOf("⌫", "0", "清空")
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                lastRow,
            )
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        KeyBox(key = key, modifier = Modifier.weight(1f)) {
                            if (key == "清空") text = "" else press(key)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .height(50.dp)
                        .background(SurfaceLow)
                        .border(1.dp, Hairline)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", fontSize = 14.sp, color = TextDim)
                }
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .height(50.dp)
                        .background(Orange)
                        .clickable { onConfirm(text) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "确定",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyBox(key: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(SurfaceHighest)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            fontFamily = if (key.length > 1) FontFamily.SansSerif else Mono,
            fontSize = if (key.length > 1) 14.sp else 22.sp,
            color = TextPrimary,
        )
    }
}
