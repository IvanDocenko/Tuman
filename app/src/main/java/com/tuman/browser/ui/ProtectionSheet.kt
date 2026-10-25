package com.tuman.browser.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tuman.browser.data.Customization
import com.tuman.browser.engine.TabInfo
import com.tuman.browser.ui.theme.tr

@Composable
fun MaximumProtectionDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
        title = { RevealText(tr("A little heads-up", "Небольшое предупреждение")) },
        text = { RevealText(tr("Maximum protection blocks more trackers and turns off website scripts. Some videos, sign-ins and buttons may stop working. You can switch back at any time.", "Максимальная защита блокирует больше трекеров и отключает скрипты сайтов. Некоторые видео, вход в аккаунт и кнопки могут перестать работать. Режим можно сменить в любой момент.")) },
        confirmButton = { TextButton(onClick = onConfirm) { RevealText(tr("Enable maximum", "Включить максимальную")) } },
        dismissButton = { TextButton(onClick = onDismiss) { RevealText(tr("Keep current", "Оставить текущую")) } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionSheet(tab: TabInfo?, settings: Customization, onDismiss: () -> Unit, onSettings: () -> Unit) {
    val localHome = tab == null || tab.url == "about:blank"
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        EntranceSequence {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(52.dp).revealOnEnter(), tint = MaterialTheme.colorScheme.primary)
                RevealText(tr("Your shield", "Ваш щит"), style = MaterialTheme.typography.headlineLarge)
                RevealText(protectionTitle(settings.protection) + tr(" protection", " защита"), color = MaterialTheme.colorScheme.primary)
                if (localHome) RevealText(tr("Ready to protect your next page.", "Готов защитить вашу следующую страницу."))
                else {
                    RevealText(runCatching { java.net.URI(tab.url).host }.getOrNull() ?: tr("Current page", "Текущая страница"), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (tab.connectionSecure) Icons.Rounded.Lock else Icons.Rounded.Info, null, Modifier.size(20.dp))
                        RevealText(if (tab.connectionSecure) tr("Encrypted connection", "Соединение зашифровано") else tr("Checking the connection", "Проверяем соединение"))
                    }
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary.copy(alpha = .10f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            RevealText(if (tab.blockedItems >= 1000) "1000+" else tab.blockedItems.toString(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                            RevealText(tr("blocked items on this page", "заблокировано на этой странице"))
                        }
                    }
                    RevealText(if (tab.blockedItems == 0) tr("No blocked items reported on this page yet.", "На этой странице пока нет зарегистрированных блокировок.")
                        else tr("Trackers, restricted cookies and unsafe content reported by the browser. The counter resets when the page reloads.", "Трекеры, запрещённые cookies и опасное содержимое, о блокировке которых сообщил браузер. Счётчик обновляется при перезагрузке страницы."), style = MaterialTheme.typography.bodySmall)
                    if (tab.blockedHosts.isNotEmpty()) {
                        HorizontalDivider()
                        RevealText(tr("Blocked sources", "Заблокированные источники"), style = MaterialTheme.typography.titleMedium)
                        tab.blockedHosts.forEach { RevealText(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { RevealText(tr("Protection settings", "Настройки защиты")) }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
