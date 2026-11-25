package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs on the PC, no network: reading each translation service's answers. */
class TranslatorsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun readsAnOpenAiStyleAnswer() {
        val answer = """{"choices":[{"message":{"role":"assistant","content":"```json\n{\"style\":\"rap\",\"lines\":[{\"n\":3,\"zh\":\"冲\"}]}\n```"}}],
            "usage":{"total_tokens":321}}"""
        val (content, tokens) = Translators.parseOpenAiChat(answer)
        assertEquals(321, tokens)
        val ai = AiTranslator(tmp.root)
        assertEquals(mapOf(3 to "冲"), ai.parse(Translators.jsonIn(content), setOf(3)))
    }

    @Test fun readsMicrosoftBaiduAndGoogle() {
        assertEquals(listOf("你好", null),
            Translators.parseMicrosoft("""[{"translations":[{"text":"你好","to":"zh-Hans"}]},{"translations":[]}]""", 2))
        assertEquals(listOf("你好", "世界"),
            Translators.parseBaidu("""{"from":"en","to":"zh","trans_result":[{"src":"hello","dst":"你好"},{"src":"world","dst":"世界"}]}""", 2))
        // Google splits the text into pieces; joined back they are our lines
        assertEquals(listOf("你好", "世界"),
            Translators.parseGoogle("""[[["你好\n","hello\n",null,null,10],["世界","world",null,null,10]],null,"en"]""", 2))
        // lines that don't line up are not trusted
        assertEquals(listOf(null, null), Translators.parseGoogle("""[[["你好世界","hello world"]]]""", 2))
    }

    @Test fun reportsBaiduErrors() {
        try {
            Translators.parseBaidu("""{"error_code":"52003","error_msg":"UNAUTHORIZED USER"}""", 1)
            fail("should have thrown")
        } catch (e: TranslatorException) {
            assertEquals(52003, e.status)
        }
    }

    @Test fun signsBaiduRequestsAndPicksDeeplHost() {
        // MD5 of appid + q + salt + key, as Baidu's documentation describes
        assertEquals("f89f9594663708c1605f3d736d01d2d4", Translators.baiduSign("2015063000000001", "apple", "1435660288", "12345678"))
        assertEquals("https://api-free.deepl.com", Translators.deeplHost("abc-123:fx"))
        assertEquals("https://api.deepl.com", Translators.deeplHost("abc-123"))
    }

    @Test fun eachServiceNeedsWhatItNeeds() {
        assertTrue(ProviderConfig(Provider.GOOGLE).ready)
        assertFalse(ProviderConfig(Provider.DEEPL).ready)
        assertTrue(ProviderConfig(Provider.DEEPL, key = "k").ready)
        assertEquals("openai:deepseek-chat", ProviderConfig(Provider.OPENAI, model = "deepseek-chat").storeTag)
        assertEquals(Provider.CLAUDE, Provider.of("nonsense"))
        assertEquals(Provider.BAIDU, Provider.of("baidu"))
    }
}
