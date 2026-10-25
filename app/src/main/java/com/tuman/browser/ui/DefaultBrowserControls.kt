package com.tuman.browser.ui

import android.app.role.RoleManager
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tuman.browser.ui.theme.tr

/** Android owns the browser role. Both granting and changing it remain explicit system choices. */
@Composable
fun DefaultBrowserControls() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var isDefault by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun refresh() {
        isDefault = runCatching {
            if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
            else context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, "https://example.com".toUri()),
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
        }.getOrDefault(false)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }
    fun openSystemSettings() {
        failed = false
        try { launcher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
        catch (_: android.content.ActivityNotFoundException) {
            try { launcher.launch(Intent(Settings.ACTION_SETTINGS)) }
            catch (_: android.content.ActivityNotFoundException) { failed = true }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer); refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    RevealText(if (isDefault) tr("Tuman is your default browser", "Tuman — браузер по умолчанию")
        else tr("Open links from other apps in Tuman", "Открывайте ссылки из других приложений в Tuman"))
    Button(enabled = !isDefault, onClick = {
        failed = false
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                try { launcher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) }
                catch (_: android.content.ActivityNotFoundException) { openSystemSettings() }
            } else openSystemSettings()
        } else openSystemSettings()
    }) { RevealText(tr("Make default browser", "Сделать браузером по умолчанию")) }
    TextButton(onClick = ::openSystemSettings) { RevealText(tr("Choose another default browser", "Выбрать другой браузер по умолчанию")) }
    RevealText(tr("Android will ask you to confirm. You can choose another browser at any time in its default apps settings.",
        "Android попросит подтвердить выбор. Вернуть другой браузер можно в любой момент в настройках приложений по умолчанию."), style = MaterialTheme.typography.bodySmall)
    if (failed) RevealText(tr("Open Android Settings → Apps → Default apps → Browser.", "Откройте настройки Android → Приложения → Приложения по умолчанию → Браузер."))
}
