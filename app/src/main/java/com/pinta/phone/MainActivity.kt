package com.pinta.phone

import android.graphics.Color
import android.os.Bundle
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
