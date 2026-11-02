package com.pinta.phone.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 各页面的 EVENT 日志通道（座舱用 Ble.log 记录链路事件，这里管其余页面）。
 * 格式与链路日志一致：`HH:mm:ss.SSS  文本`，最多保留 120 行。
 */
enum class LogChannel(val sheetTitle: String, val emptyText: String) {
    Tune("TUNE LOG", "NO TUNE EVENT"),
    Scope("SCOPE LOG", "NO SCOPE EVENT"),
    Setup("SYSTEM LOG", "NO SYSTEM EVENT"),
}

object UiLog {

    private const val MAX_LINES = 120
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val states = mutableMapOf<LogChannel, MutableStateFlow<List<String>>>()

    private fun stateOf(channel: LogChannel): MutableStateFlow<List<String>> =
        states.getOrPut(channel) { MutableStateFlow(emptyList()) }

    fun flow(channel: LogChannel): StateFlow<List<String>> = stateOf(channel).asStateFlow()

    @Synchronized
    fun note(channel: LogChannel, message: String) {
        val s = stateOf(channel)
        val line = "${timeFmt.format(Date())}  $message"
        val next = s.value + line
        s.value = if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
    }

    @Synchronized
    fun clear(channel: LogChannel) {
        stateOf(channel).value = emptyList()
    }
}
