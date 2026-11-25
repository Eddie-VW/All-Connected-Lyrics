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
import com.autosynced.lyrics.tr
import com.autosynced.lyrics.trf
import com.autosynced.lyrics.AI_PRESETS
import com.autosynced.lyrics.ApiKeyStore
import com.autosynced.lyrics.Lang
import com.autosynced.lyrics.LyricsRepo
import com.autosynced.lyrics.Prefs
import com.autosynced.lyrics.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** What each service is like, where its key comes from, and whether it works in mainland China. */
private fun about(p: Provider): String = when (p) {
    Provider.CLAUDE -> tr("Best at matching each song's genre and tone. Key from console.anthropic.com → API Keys. " +
        "A Max plan includes monthly API credit; otherwise about a cent or a few per song.",
        "最能贴合每首歌的曲风和语气。密钥在 console.anthropic.com → API Keys 获取。Max 套餐每月含 API 额度，" +
            "否则每首歌约几美分。")
    Provider.OPENAI -> tr("DeepSeek, SiliconFlow, Gemini, Groq, OpenRouter and others. Gets the same genre-style " +
        "instructions as Claude. Pick a service below, then paste a key from its website.",
        "DeepSeek、硅基流动、Gemini、Groq、OpenRouter 等。与 Claude 使用相同的曲风翻译要求。在下面选择服务，" +
            "再粘贴从它网站获取的密钥。")
    Provider.DEEPL -> tr("Plain, literal style. DeepL API Free: 500,000 characters a month (hundreds of songs). " +
        "Key from deepl.com → API (free keys end in “:fx”). Usually works in mainland China.",
        "直译风格。DeepL API 免费版每月 50 万字符（几百首歌）。密钥在 deepl.com → API 获取（免费密钥以“:fx”结尾）。" +
            "中国大陆通常可用。")
    Provider.MICROSOFT -> tr("Plain style. Azure Translator's free tier: about 2 million characters a month. Create a " +
        "Translator resource at portal.azure.com; copy its key and its region. Usually works in mainland China.",
        "直译风格。Azure 翻译免费层每月约 200 万字符。在 portal.azure.com 创建“翻译”资源，复制它的密钥和区域。" +
            "中国大陆通常可用。")
    Provider.BAIDU -> tr("Plain style, strong on Chinese. Free monthly allowance once your account is verified at " +
        "fanyi-api.baidu.com; the developer info page shows the App ID and the key. Works in mainland China.",
        "直译风格，中文表现好。在 fanyi-api.baidu.com 完成认证后每月有免费额度；管理控制台 → 开发者信息 里有 APP ID 和密钥。" +
            "中国大陆可用。")
    Provider.GOOGLE -> tr("Plain style, nothing to set up. Uses Google Translate's free web access, which isn't an " +
        "official service and could stop working. Not reachable from mainland China without a VPN.",
        "直译风格，无需设置。使用谷歌翻译的免费网页接口，并非官方服务，随时可能失效。中国大陆需要 VPN 才能使用。")
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
    val target by Prefs.targetLang.collectAsState()

    Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(tr("‹ Back", "‹ 返回"), color = pal.accent, fontSize = 16.sp) }
            Text(tr("Translation", "翻译"), color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Section(tr("Translation", "翻译")) {
                SwitchRow(tr("Show translation", "显示翻译"), translateOn,
                    tr("A translation under each line, in the app, the floating lyrics and the lock screen.",
                        "在应用、悬浮歌词和锁屏上，每行下面显示翻译。")) { Prefs.setTranslate(context, it) }
                Label(tr("Translate into", "翻译成"))
                ValueChips(Lang.LANGUAGES.map { it.first }, target, { Lang.nameOf(it) }) { Prefs.setTargetLang(context, it) }
                Hint(if (target == "zh") tr("Human translations from NetEase, QQ Music and Kugou are used first; the service below " +
                    "fills in the rest.", "优先使用网易云、QQ 音乐和酷狗的人工翻译，其余由下面的服务补全。")
                    else tr("Music sites only have Simplified Chinese translations, so the service below translates every line.",
                        "音乐网站只有简体中文翻译，所以每一行都由下面的服务翻译。"))
                SwitchRow(tr("Translate with a service", "使用翻译服务"), aiOn,
                    tr("Lines without a human translation are translated by the service you choose below.",
                        "没有人工翻译的歌词，由下面选择的服务翻译。")) { Prefs.setAiTranslate(context, it) }
            }
            if (aiOn) {
                Section(tr("Service", "服务")) {
                    for (p in Provider.entries) ProviderRow(p, p == provider) { Prefs.setProvider(context, p) }
                }
                Section(tr(provider.labelEn, provider.labelZh)) { ProviderSetup(provider) }
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
            Text(tr(p.labelEn, p.labelZh), color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (p.styleAware) tr("AI · matches the song's style", "AI · 贴合歌曲风格") else tr("Translator · plain style", "翻译引擎 · 直译风格"),
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
        Label(tr("AI service", "AI 服务"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in AI_PRESETS) Chip(tr(a.name, a.nameZh), preset == a.name, null) {
                preset = a.name; saved = false
                if (a.baseUrl.isNotEmpty()) { baseUrl = a.baseUrl; model = a.model }
            }
        }
        AI_PRESETS.firstOrNull { it.name == preset }?.let { a ->
            Hint(tr(a.noteEn, a.noteZh) + (if (a.keyFrom.isNotEmpty()) trf(" Key from {0}.", "密钥在 {0} 获取。", a.keyFrom) else ""))
        }
        Field(tr("Address (base URL)", "地址（Base URL）"), baseUrl, "https://…/v1") { baseUrl = it; saved = false }
        Field(tr("Model", "模型"), model, tr("model name", "模型名")) { model = it; saved = false }
        Hint(tr("Model names change now and then. If the service says the model doesn't exist, copy a current " +
            "name from its website into this box.", "模型名称偶尔会变。如果服务提示模型不存在，请从它的网站复制当前的模型名填到这里。"))
    }

    if (p.extraEn != null) Field(tr(p.extraEn, p.extraZh ?: p.extraEn), extra, "") { extra = it; saved = false }

    if (p.needsKey) {
        Field(if (p == Provider.BAIDU) tr("Secret key", "密钥") else tr("API key", "API 密钥"), key,
            if (hasKey) tr("••••••••  (saved; paste to replace)", "••••••••（已保存；粘贴新密钥可替换）") else tr("paste your key here", "在这里粘贴密钥"), secret = true) {
            key = it.trim(); saved = false
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = {
            if (p.needsKey && key.isNotEmpty()) { ApiKeyStore.set(context, key, slot); key = ""; hasKey = true }
            if (p.extraEn != null) Prefs.setProviderExtra(context, p, extra)
            if (p == Provider.OPENAI) Prefs.setAi(context, preset, baseUrl, model)
            saved = true
            LyricsRepo.retranslate()
        }) { Text(tr("Save", "保存"), color = pal.accent, fontWeight = FontWeight.Bold) }
        if (p.needsKey && hasKey) TextButton(onClick = { ApiKeyStore.set(context, null, slot); hasKey = false }) {
            Text(tr("Remove key", "删除密钥"), color = pal.sub)
        }
        if (saved) Text(tr("Saved ✓", "已保存 ✓"), color = pal.sub, fontSize = 13.sp)
    }

    val dollars = spent?.first ?: 0.0
    if (p == Provider.CLAUDE && dollars > 0) {
        Text(tr("Claude translation so far: US$", "Claude 翻译累计花费：US$") + String.format(Locale.US, "%.4f", dollars), fontSize = 12.sp, color = pal.faint)
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
