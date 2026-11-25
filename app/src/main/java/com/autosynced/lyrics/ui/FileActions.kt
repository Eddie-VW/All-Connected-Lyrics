package com.autosynced.lyrics.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.autosynced.lyrics.CustomCovers
import com.autosynced.lyrics.Line
import com.autosynced.lyrics.LyricFiles
import com.autosynced.lyrics.NowPlaying
import com.autosynced.lyrics.tr
import com.autosynced.lyrics.trf

/** Import a lyrics file, export lyrics as a file, pick a cover picture: the phone's own file pickers. */
class FileActions(val importLyrics: () -> Unit, val exportLyrics: (type: String) -> Unit, val pickCover: () -> Unit)

private fun displayName(c: Context, uri: Uri): String =
    c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment ?: "lyrics.txt"

/**
 * [onImported] gets the lines of a lyrics file the user picked; [toExport] gives the lines to save;
 * a picked cover picture becomes the cover of the song playing (or its album).
 */
@Composable
fun rememberFileActions(onImported: (List<Line>) -> Unit, toExport: () -> List<Line>): FileActions {
    val context = LocalContext.current
    var exportType by remember { mutableStateOf("lrc") }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayName(context, uri)
        val lines = try {
            context.contentResolver.openInputStream(uri)?.use { LyricFiles.read(name, it.readBytes().toString(Charsets.UTF_8)) }
        } catch (_: Exception) { null }
        if (lines.isNullOrEmpty()) {
            Toast.makeText(context, tr("No lyrics found in that file (try .lrc, .srt, .vtt, .ass or .txt)",
                "这个文件里没有找到歌词（支持 .lrc、.srt、.vtt、.ass、.txt）"), Toast.LENGTH_LONG).show()
        } else {
            onImported(lines)
            Toast.makeText(context, trf("Imported {0} lines", "已导入 {0} 行", lines.size), Toast.LENGTH_SHORT).show()
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val track = NowPlaying.state.value.track
        val ok = try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(LyricFiles.write(exportType, toExport(), track?.title.orEmpty(), track?.artist.orEmpty()).toByteArray())
            } != null
        } catch (_: Exception) { false }
        Toast.makeText(context, if (ok) tr("Lyrics saved", "歌词已保存") else tr("Couldn't save the file", "无法保存文件"),
            Toast.LENGTH_SHORT).show()
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val track = NowPlaying.state.value.track
        if (uri == null || track == null) return@rememberLauncherForActivityResult
        val ok = CustomCovers.set(context, uri, track.title, track.artist, track.album)
        if (ok) NowPlaying.reload()
        Toast.makeText(context, if (ok) tr("Cover changed", "封面已更换") else tr("That file isn't a picture", "这个文件不是图片"),
            Toast.LENGTH_SHORT).show()
    }

    return remember {
        FileActions(
            importLyrics = { importer.launch(arrayOf("*/*")) },
            exportLyrics = { type ->
                exportType = type
                val t = NowPlaying.state.value.track
                val base = if (t == null) "lyrics" else "${t.artist} - ${t.title}".replace(Regex("""[\\/:*?"<>|]"""), "_")
                exporter.launch("$base.$type")
            },
            pickCover = { coverPicker.launch("image/*") },
        )
    }
}
