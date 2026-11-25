package com.autosynced.lyrics.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autosynced.lyrics.AI_PRESETS
import com.autosynced.lyrics.ApiKeyStore
import com.autosynced.lyrics.LyricsRepo
import com.autosynced.lyrics.Prefs
import com.autosynced.lyrics.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** What each service is like, where its key comes from, and whether it works in mainland China. */
private fun about(p: Provider): String = when (p) {
    Provider.CLAUDE -> "Best at matching each song's genre and tone. Key from console.anthropic.com → API Keys. " +
        "A Max plan includes monthly API credit; otherwise about a cent or a few per song."
    Provider.OPENAI -> "DeepSeek, SiliconFlow 硅基流动, Gemini, Groq, OpenRouter and others. Gets the same " +
        "genre-style instructions as Claude. Pick a service below, then paste a key from its website."
    Provider.DEEPL -> "Plain, literal style. DeepL API Free: 500,000 characters a month (hundreds of songs). " +
        "Key from deepl.com → API (free keys end in “:fx”). Usually works in mainland China."
    Provider.MICROSOFT -> "Plain style. Azure Translator's free tier: about 2 million characters a month. Create a " +
        "Translator resource at portal.azure.com; copy its key and its region. Usually works in mainland China."
    Provider.BAIDU -> "Plain style, strong on Chinese. Free monthly allowance once your account is verified at " +
        "fanyi-api.baidu.com; 管理控制台 → 开发者信息 shows the APP ID and 密钥 (the key). Works in mainland China."
    Provider.GOOGLE -> "Plain style, nothing to set up. Uses Google Translate's free web access, which isn't an " +
        "official service and could stop working. Not reachable from mainland China without a VPN."
}

/** The 译 page: translation on/off, and which service fills in lines no music site translated. */
@Composable
fun TranslationScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val pal = LocalPalette.current
    val context = LocalContext.current
    val translateOn by Prefs.translate.collectAsState()
    val aiOn by Prefs.aiTranslate.collectAsState()
    val provider by Prefs.provider.collectAsState()

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = pal.accent, fontSize = 16.sp) }
            Text("Chinese translation 中文翻译", color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section("Translation") {
                SwitchRow("Show translation", translateOn,
                    "Simplified Chinese under each line, in the app and the floating lyrics. Human translations " +
                        "from NetEase, QQ Music and Kugou are always used first.") { Prefs.setTranslate(context, it) }
                SwitchRow("Fill in lines no music site translated", aiOn,
                    "Using the service you choose below.") { Prefs.setAiTranslate(context, it) }
            }
            if (aiOn) {
                Section("Service") {
                    for (p in Provider.entries) ProviderRow(p, p == provider) { Prefs.setProvider(context, p) }
                }
                Section(provider.label) { ProviderSetup(provider) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ProviderRow(p: Provider, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
        .background(if (selected) pal.surface2 else pal.bg).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) pal.accent else pal.faint, CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(pal.accent))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.label, color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (p.styleAware) "AI · matches the song's style" else "Translator · plain style",
                color = if (p.styleAware) pal.accent else pal.faint, fontSize = 12.sp)
        }
    }
}

/** The chosen service's settings: what it is, its key (and region / App ID / AI service), saved on tap. */
@Composable
private fun ProviderSetup(p: Provider) {
    val pal = LocalPalette.current
    val context = LocalContext.current
    val slot = ApiKeyStore.slot(p)
    var hasKey by remember(p) { mutableStateOf(ApiKeyStore.has(context, slot)) }
    var key by remember(p) { mutableStateOf("") }
    var extra by remember(p) { mutableStateOf(Prefs.providerExtra(context, p)) }
    var preset by remember(p) { mutableStateOf(Prefs.aiPreset(context)) }
    var baseUrl by remember(p) { mutableStateOf(Prefs.aiBaseUrl(context)) }
    var model by remember(p) { mutableStateOf(Prefs.aiModel(context)) }
    var saved by remember(p) { mutableStateOf(false) }
    var spent by remember { mutableStateOf<Pair<Double, Int>?>(null) }
    LaunchedEffect(Unit) { spent = withContext(Dispatchers.IO) { LyricsRepo.aiSpent() } }

    Hint(about(p))

    if (p == Provider.OPENAI) {
        Label("AI service")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in AI_PRESETS) Chip(a.name, preset == a.name, null) {
                preset = a.name; saved = false
                if (a.baseUrl.isNotEmpty()) { baseUrl = a.baseUrl; model = a.model }
            }
        }
        AI_PRESETS.firstOrNull { it.name == preset }?.let { a ->
            Hint(a.note + (if (a.keyFrom.isNotEmpty()) " Key from ${a.keyFrom}." else ""))
        }
        Field("Address (base URL)", baseUrl, "https://…/v1") { baseUrl = it; saved = false }
        Field("Model", model, "model name") { model = it; saved = false }
        Hint("Model names change now and then. If the service says the model doesn't exist, copy a current " +
            "name from its website into this box.")
    }

    p.extraLabel?.let { label -> Field(label, extra, "") { extra = it; saved = false } }

    if (p.needsKey) {
        Field(if (p == Provider.BAIDU) "密钥 (secret key)" else "API key", key,
            if (hasKey) "••••••••  (saved; paste to replace)" else "paste your key here", secret = true) {
            key = it.trim(); saved = false
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            if (p.needsKey && key.isNotEmpty()) { ApiKeyStore.set(context, key, slot); key = ""; hasKey = true }
            if (p.extraLabel != null) Prefs.setProviderExtra(context, p, extra)
            if (p == Provider.OPENAI) Prefs.setAi(context, preset, baseUrl, model)
            saved = true
            LyricsRepo.retranslate()
        }) { Text("Save", color = pal.accent, fontWeight = FontWeight.Bold) }
        if (p.needsKey && hasKey) TextButton(onClick = { ApiKeyStore.set(context, null, slot); hasKey = false }) {
            Text("Remove key", color = pal.sub)
        }
        if (saved) Text("Saved ✓", color = pal.sub, fontSize = 13.sp)
    }

    val dollars = spent?.first ?: 0.0
    if (p == Provider.CLAUDE && dollars > 0) {
        Text("Claude translation so far: US$" + String.format(Locale.US, "%.4f", dollars), fontSize = 12.sp, color = pal.faint)
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String, secret: Boolean = false, onChange: (String) -> Unit) {
    val pal = LocalPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = pal.text, unfocusedTextColor = pal.text,
            focusedBorderColor = pal.accent, cursorColor = pal.accent, focusedLabelColor = pal.accent,
            unfocusedLabelColor = pal.sub),
        modifier = Modifier.fillMaxWidth(),
    )
}
