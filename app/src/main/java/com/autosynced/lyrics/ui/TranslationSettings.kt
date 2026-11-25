package com.autosynced.lyrics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.ApiKeyStore
import com.autosynced.lyrics.LyricsRepo
import com.autosynced.lyrics.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** The 译 panel: Chinese translation on/off, AI for lines no site translated, the Claude API key. */
@Composable
fun TranslationSettings(onClose: () -> Unit) {
    val pal = LocalPalette.current
    val context = LocalContext.current
    val translateOn by Prefs.translate.collectAsState()
    val aiOn by Prefs.aiTranslate.collectAsState()
    var hasKey by remember { mutableStateOf(ApiKeyStore.has(context)) }
    var keyText by remember { mutableStateOf("") }
    var spent by remember { mutableStateOf<Pair<Double, Int>?>(null) }
    LaunchedEffect(Unit) { spent = withContext(Dispatchers.IO) { LyricsRepo.aiSpent() } }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = pal.surface,
        titleContentColor = pal.text,
        textContentColor = pal.sub,
        title = { Text("Chinese translation 中文翻译", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingRow("Show translation", "Simplified Chinese under each line, in the app and the floating lyrics.",
                    translateOn) { Prefs.setTranslate(context, it) }

                SettingRow("Use AI for missing lines",
                    "Translations come from NetEase, QQ Music and Kugou first. Lines none of them translated " +
                        "are translated by Claude, in a style that fits the song's genre.",
                    aiOn, enabled = translateOn) { Prefs.setAiTranslate(context, it) }

                if (translateOn && aiOn) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Claude API key", color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (hasKey) "A key is saved (encrypted on this phone). Paste a new one to replace it."
                            else "Get one at console.anthropic.com → API Keys. AI translation is paid by that " +
                                "account: a fraction of a cent for a few lines, a few cents for a whole song, and each line is only paid for once.",
                            fontSize = 13.sp, lineHeight = 18.sp,
                        )
                        OutlinedTextField(
                            value = keyText,
                            onValueChange = { keyText = it.trim() },
                            singleLine = true,
                            placeholder = { Text(if (hasKey) "••••••••  (saved)" else "sk-ant-…") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = pal.text, unfocusedTextColor = pal.text,
                                focusedBorderColor = pal.accent, cursorColor = pal.accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(enabled = keyText.isNotEmpty(), onClick = {
                                ApiKeyStore.set(context, keyText)
                                keyText = ""
                                hasKey = true
                                LyricsRepo.retranslate()
                            }) { Text("Save key", color = if (keyText.isNotEmpty()) pal.accent else pal.faint) }
                            if (hasKey) {
                                TextButton(onClick = { ApiKeyStore.set(context, null); hasKey = false }) {
                                    Text("Remove key", color = pal.sub)
                                }
                            }
                        }
                        spent?.let { (dollars, requests) ->
                            if (requests > 0) Text(
                                "AI translation so far: US$" + String.format(Locale.US, "%.4f", dollars) +
                                    " ($requests request${if (requests == 1) "" else "s"})",
                                fontSize = 12.sp, color = pal.faint,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = pal.accent) } },
    )
}

@Composable
private fun SettingRow(title: String, detail: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) pal.text else pal.faint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(detail, color = if (enabled) pal.sub else pal.faint, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = pal.accent, checkedThumbColor = pal.accentInk))
    }
}
