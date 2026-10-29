package com.pinta.phone

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pinta.phone.ble.Ble
import com.pinta.phone.core.SpeedSettings
import com.pinta.phone.ui.PintaApp
import com.pinta.phone.ui.theme.Ink
import com.pinta.phone.ui.theme.PintaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // 前台时保持屏幕常亮：系统仍会把亮度调暗，但不会熄屏/锁屏，
        // 这样 BLE 连接和 20Hz 轮询不会因为省电/Doze 掉链子
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Ble.init(this)
        // 车速标定参数（生效轮子 / RPM per pu / 轮周长）
        SpeedSettings.init(this)
        // 冷启动自动重连上次连过的模块；没权限/蓝牙没开时这里直接跳过
        Ble.autoConnectLast()
        setContent {
            PintaTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    PintaApp()
                }
            }
        }
    }
}
