package com.autosynced.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.Line
import com.autosynced.lyrics.LyricEdits
import com.autosynced.lyrics.LyricsRepo
import com.autosynced.lyrics.LyricsState
import com.autosynced.lyrics.NowPlaying
import com.autosynced.lyrics.Prefs
import com.autosynced.lyrics.tr
import kotlinx.coroutines.delay

/** One line being edited ([id] keeps the list steady while lines are added and removed). */
private data class EditRow(val id: Long, val time: Long, val text: String, val translation: String)

private var nextId = 1L
private fun row(l: Line) = EditRow(nextId++, l.timeMs, l.text, l.translation ?: "")

/**
 * The lyrics editor (like the PC app's): fix words and translations, move lines earlier / later
 * or to "now", add and delete lines, shift the whole song, paste plain lyrics and tap-sync them.
 * Saved lyrics are kept on this phone and always used instead of the online ones.
 */
@Composable
fun LyricsEditorScreen(onBack: () -> Unit) {
    val pal = LocalPalette.current
    val playback by NowPlaying.state.collectAsState()
    val translateOn by Prefs.translate.collectAsState()
    val start = remember {
        (LyricsRepo.state.value as? LyricsState.Found)?.lyrics?.lines.orEmpty().map { row(it) }
    }
    val rows = remember { mutableStateListOf<EditRow>().apply { addAll(start) } }
    var dirty by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var tapAt by remember { mutableStateOf<Int?>(null) }     // tap-sync: the line that gets the next tap
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { now = NowPlaying.state.value.position(); delay(60) } }
    val listState = rememberLazyListState()
    val hasMine = remember { LyricsRepo.hasMine() }

    fun change(block: () -> Unit) { block(); dirty = true }
    fun save() {
        LyricsRepo.saveMine(rows.map { Line(it.time, it.text, it.translation.ifBlank { null }) })
        dirty = false
        onBack()
    }
    val back: () -> Unit = { if (dirty) { askDiscard = true } else { onBack() } }
    BackHandler(onBack = back)

    // the line playing now (by time), for highlighting
    val playingIndex = rows.indices.lastOrNull { rows[it].time in 1..now } ?: -1
    LaunchedEffect(tapAt) { tapAt?.let { if (it in rows.indices) listState.animateScrollToItem(it) } }

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding().imePadding()) {
        PageTop(tr("Edit lyrics", "编辑歌词"), back) {
            TextButton(onClick = { save() }, enabled = rows.isNotEmpty() || hasMine) {
                Text(tr("Save", "保存"), color = pal.accent, fontWeight = FontWeight.Bold)
            }
        }

        // song, position and simple controls, to find your way while editing
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(playback.track?.title ?: tr("Nothing playing", "没有正在播放的歌曲"), color = pal.text, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(LyricEdits.stamp(now) + " / " + LyricEdits.stamp(playback.track?.durationMs ?: 0), color = pal.sub, fontSize = 13.sp)
            }
            SmallButton("−5s") { NowPlaying.seekTo(now - 5_000) }
            Box(Modifier.size(42.dp).clip(CircleShape).background(pal.surface2).clickable { NowPlaying.playPause() },
                contentAlignment = Alignment.Center) { if (playback.playing) PauseIcon(pal.text) else PlayIcon(pal.text) }
            SmallButton("+5s") { NowPlaying.seekTo(now + 5_000) }
        }

        // tools
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (tapAt != null) tr("Stop tap-sync", "停止点按同步") else tr("Tap-sync", "点按同步"), tapAt != null, null) {
                tapAt = if (tapAt != null) null else 0
            }
            Chip(tr("Paste lyrics", "粘贴歌词"), false, null) { pasting = true }
            Chip(tr("All 0.5 s earlier", "全部提前 0.5 秒"), false, null) {
                change { val moved = LyricEdits.shift(rows.map { Line(it.time, "") }, -500); rows.indices.forEach { rows[it] = rows[it].copy(time = moved[it].timeMs) } }
            }
            Chip(tr("All 0.5 s later", "全部延后 0.5 秒"), false, null) {
                change { rows.indices.forEach { rows[it] = rows[it].copy(time = rows[it].time + 500) } }
            }
            if (hasMine) Chip(tr("Use online lyrics", "改用网络歌词"), false, null) { LyricsRepo.useOnline(); onBack() }
        }

        // tap-sync: tap as each line starts
        tapAt?.let { at ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(16.dp)).background(pal.surface)
                .padding(14.dp)) {
                if (at >= rows.size) {
                    Text(tr("All lines are timed. Check them, then Save.", "所有歌词行都已打上时间。检查后点保存。"), color = pal.text, fontSize = 15.sp)
                } else {
                    Text(tr("Play the song from the start, and tap when this line begins:", "从头播放这首歌，在这一行开始时点按："),
                        color = pal.sub, fontSize = 13.sp)
                    Text(rows[at].text.ifBlank { "♪" }, color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(50)).background(pal.accent)
                            .clickable { change { rows[at] = rows[at].copy(time = now.coerceAtLeast(1)) }; tapAt = at + 1 },
                            contentAlignment = Alignment.Center) {
                            Text(tr("Starts now", "现在开始"), color = pal.accentInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.height(54.dp).clip(RoundedCornerShape(50)).background(pal.surface2)
                            .clickable { if (at > 0) tapAt = at - 1 }.padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center) { Text(tr("Back one", "退回一行"), color = pal.text) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (rows.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(tr("No lyrics yet. Paste them in (from any website), then tap-sync them to the song.",
                    "还没有歌词。可以从任意网站复制后粘贴进来，再用“点按同步”对齐到歌曲。"), color = pal.sub, fontSize = 15.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(12.dp))
                Chip(tr("Paste lyrics", "粘贴歌词"), true, null) { pasting = true }
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(rows, key = { _, r -> r.id }) { i, r ->
                val highlight = i == playingIndex || i == tapAt
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.surface)
                    .border(if (highlight) 2.dp else 0.dp, if (highlight) pal.accent else pal.surface, RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // the time: tap to hear it, nudge it, or set it to "now"
                        Text(if (r.time > 0) LyricEdits.stamp(r.time) else "--:--.--", color = pal.accent, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = r.time > 0) { NowPlaying.seekTo(r.time) }
                                .padding(horizontal = 6.dp, vertical = 4.dp))
                        SmallButton("−0.1") { change { rows[i] = r.copy(time = (r.time - 100).coerceAtLeast(0)) } }
                        SmallButton("+0.1") { change { rows[i] = r.copy(time = r.time + 100) } }
                        SmallButton(tr("Now", "此刻")) { change { rows[i] = r.copy(time = now.coerceAtLeast(1)) } }
                        Spacer(Modifier.weight(1f))
                        SmallButton("＋") { change { rows.add(i + 1, EditRow(nextId++, r.time + 1, "", "")) } }
                        SmallButton("✕") { change { rows.removeAt(i) } }
                    }
                    EditField(r.text, tr("Lyric line", "歌词"), big = true) { v -> change { rows[i] = rows[i].copy(text = v) } }
                    if (translateOn || r.translation.isNotEmpty()) {
                        EditField(r.translation, tr("Translation", "翻译"), big = false) { v -> change { rows[i] = rows[i].copy(translation = v) } }
                    }
                }
            }
            item {
                TextButton(onClick = { change { rows.add(EditRow(nextId++, (rows.lastOrNull()?.time ?: 0) + 1, "", "")) } }) {
                    Text(tr("＋ Add a line at the end", "＋ 在末尾添加一行"), color = pal.accent)
                }
            }
        }
    }

    if (pasting) PasteDialog(onDismiss = { pasting = false }) { text ->
        change { rows.clear(); rows.addAll(LyricEdits.fromText(text).map { row(it) }) }
        pasting = false
        tapAt = 0
    }
    if (askDiscard) AlertDialog(
        onDismissRequest = { askDiscard = false },
        containerColor = pal.surface,
        title = { Text(tr("Save your changes?", "保存修改吗？"), color = pal.text, fontWeight = FontWeight.Bold) },
        text = { Text(tr("You changed these lyrics.", "你修改了这些歌词。"), color = pal.sub) },
        confirmButton = { TextButton(onClick = { askDiscard = false; save() }) { Text(tr("Save", "保存"), color = pal.accent) } },
        dismissButton = { TextButton(onClick = { askDiscard = false; onBack() }) { Text(tr("Discard", "放弃"), color = pal.sub) } },
    )
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(Modifier.padding(horizontal = 2.dp).heightIn(min = 34.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
        .padding(horizontal = 7.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, color = pal.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditField(value: String, placeholder: String, big: Boolean, onChange: (String) -> Unit) {
    val pal = LocalPalette.current
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(placeholder, color = pal.faint) },
        textStyle = TextStyle(color = if (big) pal.text else pal.sub, fontSize = if (big) 16.sp else 14.sp,
            fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = pal.accent, unfocusedBorderColor = pal.surface2,
            cursorColor = pal.accent),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onPaste: (String) -> Unit) {
    val pal = LocalPalette.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = pal.surface,
        title = { Text(tr("Paste lyrics", "粘贴歌词"), color = pal.text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(tr("One line per lyric line. This replaces the lines in the editor; then tap-sync them.",
                    "每行一句歌词。会替换编辑器里现有的歌词，然后用点按同步对齐时间。"), color = pal.sub, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 6, maxLines = 12,
                    textStyle = TextStyle(color = pal.text, fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = pal.accent, cursorColor = pal.accent),
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onPaste(text) }, enabled = text.isNotBlank()) {
            Text(tr("Use these lyrics", "使用这些歌词"), color = pal.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消"), color = pal.sub) } },
    )
}
