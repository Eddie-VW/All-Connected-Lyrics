package com.autosynced.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.CloudSync
import com.autosynced.lyrics.LyricsRepo
import com.autosynced.lyrics.tr
import com.autosynced.lyrics.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings → Sync with the PC: the WebDAV folder the PC app puts its lyrics in. */
@Composable
fun CloudSyncScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(CloudSync.enabled(context)) }
    var url by remember { mutableStateOf(CloudSync.url(context)) }
    var user by remember { mutableStateOf(CloudSync.user(context)) }
    var password by remember { mutableStateOf("") }
    var hasPassword by remember { mutableStateOf(CloudSync.hasPassword(context)) }
    var note by remember { mutableStateOf(CloudSync.status) }
    var busy by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf(-1) }
    LaunchedEffect(on) { count = if (on) withContext(Dispatchers.IO) { CloudSync.count(context) } else 0 }

    fun saveAndSync() {
        CloudSync.save(context, on, url, user, password.ifBlank { null })
        if (password.isNotBlank()) { hasPassword = true; password = "" }
        if (!on) { note = ""; return }
        busy = true; note = tr("Checking…", "正在检查…")
        scope.launch {
            note = LyricsRepo.syncNow()
            count = withContext(Dispatchers.IO) { CloudSync.count(context) }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop(tr("Sync with the PC", "与电脑同步"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section(tr("Cloud folder", "云盘")) {
                SwitchRow(tr("Get the PC app's lyrics", "获取电脑版的歌词"), on,
                    tr("The lyrics you synced on the PC and the ones it found online, with their translations. " +
                        "Your own edits on this phone still come first.",
                        "包括你在电脑上同步的歌词和电脑在网上找到的歌词及其翻译。你在手机上编辑的歌词仍然优先。")) {
                    on = it; saveAndSync()
                }
                Hint(tr("Use the same WebDAV folder as in the PC app (Settings → Sync to phone). Free WebDAV: Koofr (10 GB) " +
                    "or InfiniCLOUD (20 GB). Make an app password on the service's website; this phone only reads from it.",
                    "使用与电脑版相同的 WebDAV 云盘（设置 → 同步到手机）。免费的 WebDAV：Koofr（10 GB）或 InfiniCLOUD（20 GB）。" +
                        "请在该服务的网站上创建应用密码；手机只读取，不上传。"))
                Spacer(Modifier.height(8.dp))
                CloudField(tr("WebDAV address", "WebDAV 地址"), url, "https://app.koofr.net/dav/Koofr") { url = it }
                CloudField(tr("User name", "用户名"), user, tr("usually your e-mail", "通常是你的邮箱")) { user = it }
                CloudField(tr("App password", "应用密码"), password,
                    if (hasPassword) tr("••••••••  (saved; type to replace)", "••••••••（已保存；输入新密码可替换）") else "", secret = true) { password = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !busy, onClick = { on = true; saveAndSync() }) {
                        Text(tr("Save and sync now", "保存并立即同步"), color = pal.accent, fontWeight = FontWeight.Bold)
                    }
                    if (hasPassword || CloudSync.enabled(context)) TextButton(onClick = {
                        CloudSync.clear(context); on = false; url = ""; user = ""; hasPassword = false; note = ""; count = 0
                    }) { Text(tr("Remove", "移除"), color = pal.sub) }
                }
                val total = if (on && count > 0) trf("{0} songs from the PC on this phone.", "手机上有来自电脑的 {0} 首歌。", count) else ""
                if (note.isNotEmpty() || total.isNotEmpty()) Text(listOf(note, total).filter { it.isNotEmpty() }.joinToString(" "),
                    color = pal.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
                Hint(tr("Checked when the app starts and every 30 minutes as songs change; only downloads when the PC's lyrics changed.",
                    "在应用启动时以及换歌时每 30 分钟检查一次；只有电脑上的歌词有变化时才下载。"))
            }
        }
    }
}

@Composable
private fun CloudField(label: String, value: String, placeholder: String, secret: Boolean = false, onChange: (String) -> Unit) {
    val pal = LocalPalette.current
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) }, placeholder = { Text(placeholder) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = pal.text, unfocusedTextColor = pal.text,
            focusedBorderColor = pal.accent, cursorColor = pal.accent, focusedLabelColor = pal.accent, unfocusedLabelColor = pal.sub),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}
