package com.audiocontrol

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.audiocontrol.ui.ControlScreen
import com.audiocontrol.ui.theme.AppTheme
import com.audiocontrol.ui.theme.Ink

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // wall-mounted tablet
        val container = AppContainer(this, lifecycleScope)
        container.vm.start()
        setContent {
            val settings by container.settings.collectAsState()
            AppTheme(accentHue = settings.accentHue) {
                Surface(Modifier.fillMaxSize().background(Color(Ink.bg))) {
                    ControlScreen(
                        vm = container.vm,
                        settings = settings,
                        onHostChange = container::setHost,
                        onHueChange = container::setHue,
                        selectGroup = container.vm::selectGroup,
                    )
                }
            }
        }
    }
}
