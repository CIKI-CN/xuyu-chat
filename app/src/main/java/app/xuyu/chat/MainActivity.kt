package app.xuyu.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.xuyu.chat.data.CardCodec
import app.xuyu.chat.data.Legal
import app.xuyu.chat.data.Settings
import app.xuyu.chat.ui.ChatScreen
import app.xuyu.chat.ui.DisclaimerScreen
import app.xuyu.chat.ui.GuideScreen
import app.xuyu.chat.ui.ModeScreen
import app.xuyu.chat.ui.SettingsScreen
import app.xuyu.chat.ui.XyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val settings by vm.store.settings.collectAsState()
            val consent by vm.store.consent.collectAsState()
            val appVersion = remember { appVersionCode(this) }
            val needsConsent = consent.versionCode != appVersion ||
                consent.disclaimerVersion != Legal.DISCLAIMER_VERSION
            var picking by remember { mutableStateOf(false) }

            XyTheme(settings.appearance.themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    when {
                        needsConsent -> DisclaimerScreen(
                            onAccept = {
                                vm.store.acceptConsent(appVersion, Legal.DISCLAIMER_VERSION)
                            },
                            onExit = { finish() }
                        )

                        picking || settings.mode.isBlank() -> ModeScreen(
                            current = settings.mode,
                            onPick = { mode ->
                                vm.applySettings(vm.store.settings.value.copy(mode = mode))
                                if (mode == Settings.MODE_RP) vm.store.ensureRpPersona()
                                vm.openMode(mode)
                                picking = false
                            },
                            onCancel = if (settings.mode.isBlank()) null else ({ picking = false })
                        )

                        else -> AppContent(vm, onSwitchMode = { picking = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val payload = CardCodec.payloadFromIntent(intent) ?: return
        vm.pendingImport.value = payload
        intent?.action = Intent.ACTION_MAIN
    }

    private fun appVersionCode(context: Context): Int = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    } catch (e: Exception) {
        0
    }
}

@Composable
private fun AppContent(vm: ChatViewModel, onSwitchMode: () -> Unit) {
    val consent by vm.store.consent.collectAsState()
    var screen by remember { mutableStateOf(if (consent.guideSeen) "chat" else "guide") }
    var guideFrom by remember { mutableStateOf("chat") }

    when (screen) {
        "settings" -> SettingsScreen(
            vm = vm,
            onBack = { screen = "chat" },
            onOpenGuide = {
                guideFrom = "settings"
                screen = "guide"
            },
            onSwitchMode = onSwitchMode
        )

        "guide" -> GuideScreen(onClose = {
            vm.store.markGuideSeen()
            screen = if (guideFrom == "settings") "settings" else "chat"
        })

        else -> ChatScreen(
            vm = vm,
            onOpenSettings = { screen = "settings" },
            onSwitchMode = onSwitchMode
        )
    }
    IncomingCardDialog(vm)
}

@Composable
private fun IncomingCardDialog(vm: ChatViewModel) {
    val pending by vm.pendingImport.collectAsState()
    val payload = pending ?: return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) vm.pendingImport.value = null },
        title = { Text("收到一张角色卡") },
        text = {
            Text("要导入吗？会加进当前模式的角色库（接口和外观设置也会跟着这张卡走）。")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (busy) return@TextButton
                    busy = true
                    scope.launch {
                        val r = vm.importCard(payload)
                        vm.pendingImport.value = null
                        busy = false
                        r.onSuccess {
                            Toast.makeText(ctx, "已导入「${it.activeFor(it.mode).name}」", Toast.LENGTH_SHORT).show()
                            vm.newChat()
                        }.onFailure {
                            Toast.makeText(ctx, it.message ?: "导入失败", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(onClick = { vm.pendingImport.value = null }) { Text("忽略") }
        }
    )
}
