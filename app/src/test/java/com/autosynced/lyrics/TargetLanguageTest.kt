package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs on the PC: translating lyrics into languages other than Chinese. */
class TargetLanguageTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun skipsLinesAlreadyInTheTargetLanguage() {
        assertFalse(TextRules.worthTranslatingInto("사랑해요 정말로", "ko"))
        assertTrue(TextRules.worthTranslatingInto("I love you", "ko"))
        assertFalse(TextRules.worthTranslatingInto("Я тебя люблю", "ru"))
        assertTrue(TextRules.worthTranslatingInto("我爱你", "ru"))
        assertTrue(TextRules.worthTranslatingInto("I love you", "es"))          // Latin script: can't tell, so translate
        assertFalse(TextRules.worthTranslatingInto("la la la", "fr"))           // sounds never
        assertFalse(TextRules.worthTranslatingInto("我爱你", "zh"))              // Chinese rules as before
        assertTrue(TextRules.isSongIn(listOf(Line(0, "사랑해요"), Line(1, "보고 싶어")), "ko"))
        assertFalse(TextRules.isSongIn(listOf(Line(0, "Hello world")), "en"))
    }

    @Test fun servicesGetTheirOwnLanguageCodes() {
        assertEquals("ZH-HANS", Translators.code(Provider.DEEPL, "zh"))
        assertEquals("JA", Translators.code(Provider.DEEPL, "ja"))
        assertEquals("zh-Hant", Translators.code(Provider.MICROSOFT, "zh-Hant"))
        assertEquals("kor", Translators.code(Provider.BAIDU, "ko"))
        assertEquals("ara", Translators.code(Provider.BAIDU, "ar"))
        assertEquals("zh-TW", Translators.code(Provider.GOOGLE, "zh-Hant"))
        assertEquals("claude", ProviderConfig(Provider.CLAUDE).storeTag)                 // Chinese: same cache as before
        assertEquals("claude:ja", ProviderConfig(Provider.CLAUDE, target = "ja").storeTag)
    }

    @Test fun readsAnswersInAnyLanguage() {
        val ai = AiTranslator(tmp.root)
        val lines = listOf(Line(0, "Hello"), Line(1, "Hola amigo"))
        // "tr" field; a line handed back unchanged was already in the target language
        assertEquals(mapOf(0 to "Hola"), ai.parse("""{"style":"pop","lines":[{"n":0,"tr":"Hola"},{"n":1,"tr":"Hola amigo"}]}""",
            setOf(0, 1), lines, "es"))
        assertTrue(AiTranslator.systemPrompt("ja").contains("Japanese"))
        assertTrue(AiTranslator.systemPrompt("zh").contains("Simplified Chinese"))
    }
}
