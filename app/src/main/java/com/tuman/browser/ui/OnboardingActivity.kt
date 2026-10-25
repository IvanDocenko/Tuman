package com.tuman.browser.ui

import android.content.Intent
import android.os.Bundle
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
import com.tuman.browser.MainActivity
import com.tuman.browser.TumanApplication
import com.tuman.browser.data.Customization
import com.tuman.browser.ui.theme.TumanTheme
import kotlinx.coroutines.flow.first

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            val store = (application as TumanApplication).customization
            var initial by remember { mutableStateOf<Customization?>(null) }
            var error by remember { mutableStateOf(false) }
            var retry by remember { mutableIntStateOf(0) }
            LaunchedEffect(retry) {
                error = false
                try { initial = store.settings.first() }
                catch (failure: Exception) {
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    error = true
                }
            }
            LaunchedEffect(initial?.allowScreenshots) {
                if (initial?.allowScreenshots == true) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            TumanTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (error) Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                        RevealText("Could not read settings / Не удалось прочитать настройки")
                        Button(onClick = { retry++ }) { RevealText("Retry / Повторить") }
                    } else initial?.let { settings ->
                        OnboardingScreens(settings) { selected ->
                            store.save(selected.copy(isOnboarded = true))
                            if (!settings.isOnboarded) {
                                startActivity(Intent(this@OnboardingActivity, MainActivity::class.java).apply { data = intent.data })
                            }
                            finish()
                        }
                    } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }
}
