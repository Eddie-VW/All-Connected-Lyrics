package com.autosynced.lyrics.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.autosynced.lyrics.BuildConfig
import com.autosynced.lyrics.R
import com.autosynced.lyrics.tr
import com.autosynced.lyrics.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Where new versions are published (its "latest release"). */
private const val RELEASES_API = "https://api.github.com/repos/Eddie-VW/All-Connected-Lyrics/releases/latest"

/** What the update check found. */
private sealed class Update {
    data object Checking : Update()
    data object UpToDate : Update()
    data class Available(val version: String, val url: String) : Update()
    data object Failed : Update()
}

/** Releases are tagged "v<version>-build<n>"; this build is n = versionCode - 1. */
private fun buildOf(tag: String): Int? = Regex("""build(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull()

private fun checkForUpdate(): Update = try {
    val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000; conn.readTimeout = 8000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    try {
        if (conn.responseCode != 200) Update.Failed
        else {
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name")
            val latest = buildOf(tag)
            val mine = BuildConfig.VERSION_CODE - 1
            if (latest != null && latest > mine) Update.Available(tag.removePrefix("v").substringBefore("-build"),
                j.optString("html_url")) else Update.UpToDate
        }
    } finally { conn.disconnect() }
} catch (_: Exception) { Update.Failed }

/** Settings → About: the app, its version, a check for updates, and who made it. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<Update?>(null) }
    val icon = remember { ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(256, 256) }

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        PageTop(tr("About", "关于"), onBack)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) Image(icon.asImageBitmap(), null, Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(8.dp))
            Text(tr("Lyrics Master", "歌词大师"), color = pal.text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(trf("Version {0} (build {1})", "版本 {0}（构建 {1}）", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE - 1),
                color = pal.sub, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Chip(if (update == Update.Checking) tr("Checking…", "正在检查…") else tr("Check for updates", "检查更新"),
                update is Update.Available, null) {
                val u = update
                if (u is Update.Available) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                } else if (u != Update.Checking) {
                    update = Update.Checking
                    scope.launch { update = withContext(Dispatchers.IO) { checkForUpdate() } }
                }
            }
            when (val u = update) {
                Update.UpToDate -> Text(tr("You have the latest version.", "已是最新版本。"), color = pal.sub, fontSize = 13.sp)
                is Update.Available -> Text(trf("Version {0} is out. Tap above to download it.", "新版本 {0} 已发布，点上面的按钮下载。", u.version),
                    color = pal.accent, fontSize = 13.sp)
                Update.Failed -> Text(tr("Couldn't check for updates (no internet, or the download page isn't public).",
                    "无法检查更新（没有网络，或下载页面未公开）。"), color = pal.sub, fontSize = 13.sp)
                else -> {}
            }
            Spacer(Modifier.height(28.dp))
            Text(tr("Developer", "开发者"), color = pal.faint, fontSize = 12.sp)
            Text("Edward J.Y. Wallen", color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))
            Text(tr("Lyrics come from LRCLIB, NetEase Cloud Music, QQ Music and Kugou. Lyrics are copyrighted by their " +
                "owners; this app shows them for your personal use.",
                "歌词来自 LRCLIB、网易云音乐、QQ 音乐和酷狗。歌词版权归其所有者，本应用仅供个人使用。"),
                color = pal.faint, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}
