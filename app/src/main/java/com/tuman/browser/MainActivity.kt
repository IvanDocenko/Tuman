package com.tuman.browser

import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuman.browser.data.Customization
import com.tuman.browser.engine.TumanSessionManager
import com.tuman.browser.ui.RevealText
import com.tuman.browser.ui.LaunchIntro
import androidx.compose.runtime.saveable.rememberSaveable
import com.tuman.browser.ui.BrowserScreen
import com.tuman.browser.ui.OnboardingActivity
import com.tuman.browser.ui.theme.TumanTheme
import kotlinx.coroutines.flow.catch

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        incoming = intent.dataString
        setContent {
            val app = application as TumanApplication
            var settings by remember { mutableStateOf<Customization?>(null) }
            var failure by remember { mutableStateOf(false) }
            var attempt by remember { mutableIntStateOf(0) }
            LaunchedEffect(attempt) {
                failure = false
                app.customization.settings.catch { failure = true }.collect { settings = it }
            }
            LaunchedEffect(settings?.allowScreenshots) {
                if (settings?.allowScreenshots == true) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            var introFinished by rememberSaveable { mutableStateOf(false) }
            TumanTheme(settings ?: Customization()) {
                val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
                Surface(Modifier.fillMaxSize()) {
                    val current = settings
                    when {
                        failure -> Column(Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            RevealText("Could not read settings / Не удалось прочитать настройки")
                            Button(onClick = { attempt++ }) { RevealText("Retry / Повторить") }
                        }
                        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        !current.isOnboarded -> {
                            LaunchedEffect(Unit) {
                                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java).apply { data = intent.data })
                                finish()
                            }
                        }
                        else -> {
                            val manager: TumanSessionManager = viewModel()
                            Box(Modifier.fillMaxSize()) {
                                BrowserScreen(manager, current, app.customization, incoming) { incoming = null; intent.data = null }
                                if (current.intro && !introFinished) LaunchIntro(current) { introFinished = true }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = intent.dataString
    }
}
