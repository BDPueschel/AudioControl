package com.audiocontrol

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.audiocontrol.ui.ControlScreen
import com.audiocontrol.ui.theme.AppTheme
import com.audiocontrol.ui.theme.LocalInkBg
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(this, lifecycleScope)
        container.vm.start()
        setContent {
            val settings by container.settings.collectAsState()
            val scenes by container.scenes.collectAsState()
            LaunchedEffect(settings.keepAwake) {
                if (settings.keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            LaunchedEffect(settings.orientation) {
                requestedOrientation = when (settings.orientation) {
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
            }
            AppTheme(accentHue = settings.accentHue, oledBlack = settings.oledBlack) {
                Surface(Modifier.fillMaxSize(), color = Color(LocalInkBg.current)) {
                    ControlScreen(
                        vm = container.vm,
                        settings = settings,
                        scenes = scenes,
                        sceneActions = container.sceneActions,
                        settingsActions = container.settingsActions,
                        onHueChange = container::setHue,
                        onOledChange = container::setOled,
                        selectGroup = container.vm::selectGroup,
                    )
                }
            }
        }
    }

    // Cancel the VM's viewModelScope on genuine Activity destroy to stop the 5 s health-poll
    // loop. The manifest already declares configChanges for orientation/screenSize/screenLayout/
    // keyboardHidden, so onDestroy is only reached on genuine process exit / back-press.
    override fun onDestroy() {
        super.onDestroy()
        container.vm.viewModelScope.cancel()
    }
}
