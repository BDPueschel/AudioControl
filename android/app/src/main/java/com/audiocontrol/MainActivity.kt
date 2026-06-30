package com.audiocontrol

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // wall-mounted tablet
        container = AppContainer(this, lifecycleScope)
        container.vm.start()
        setContent {
            val settings by container.settings.collectAsState()
            AppTheme(accentHue = settings.accentHue, oledBlack = settings.oledBlack) {
                Surface(Modifier.fillMaxSize(), color = Color(LocalInkBg.current)) {
                    ControlScreen(
                        vm = container.vm,
                        settings = settings,
                        onHostChange = container::setHost,
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
