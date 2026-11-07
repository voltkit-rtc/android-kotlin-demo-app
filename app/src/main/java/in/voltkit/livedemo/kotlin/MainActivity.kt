package `in`.voltkit.livedemo.kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import `in`.voltkit.livedemo.kotlin.rtc.PermissionAsker
import `in`.voltkit.livedemo.kotlin.screens.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        PermissionAsker.register(this)
        setContent { AppRoot() }
    }

    override fun onDestroy() {
        PermissionAsker.unregister(this)
        super.onDestroy()
    }
}
