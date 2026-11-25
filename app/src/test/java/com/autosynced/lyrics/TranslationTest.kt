package com.autosynced.lyrics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.zip.Deflater

/** Runs on the PC, no network: matching site translations to our lines, and which lines need one. */
class TranslationTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun useJdkBase64() {
        LyricsSources.base64Decode = { java.util.Base64.getMimeDecoder().decode(it) }
    }

    private fun lines(vararg p: Pair<Long, String>) = p.map { Line(it.first, it.second) }

    @Test fun pairsNeteaseLyricsWithTranslation() {
        val lrc = "[00:01.00]I walk alone\n[00:04.50]Down the empty street\n[00:08.00]Oh\n[00:10.00]Nobody else"
        val tlrc = "[00:01.00]我独自走着\n[00:04.50]走过空荡的街\n[00:08.00]//\n[00:10.00]再没有别人"
        val got = TranslationSources.pair(lrc, tlrc)!!
        assertEquals(4, got.size)
        assertEquals("我独自走着", got[0].translation)
        assertNull(got[2].translation)                    // "//" means "no translation"
        assertNull(TranslationSources.pair(lrc, ""))
    }

    @Test fun matchesASiteTimedDifferently() {
        val ours = lines(10_000L to "I walk alone tonight", 14_000L to "Down the empty street again",
            18_000L to "Nobody else is waiting here", 22_000L to "Under the yellow light")
        // their copy has a 2.5 s longer intro
        val theirs = ours.mapIndexed { i, l -> SiteLine(l.timeMs + 2_500, l.text, "译$i") }
        assertEquals(2_500L, TranslationMatcher.timingOffset(ours, theirs))
        val got = TranslationMatcher.attach(ours, theirs)
        assertEquals(listOf("译0", "译1", "译2", "译3"), got.map { it.translation })
    }

    @Test fun joinsALineTheSiteSplitInTwo() {
        val ours = lines(1_000L to "I keep running running through the night", 6_000L to "Next line here")
        val theirs = listOf(SiteLine(1_000, "I keep running running", "我一直奔跑"),
            SiteLine(3_000, "through the night", "穿过黑夜。"), SiteLine(6_000, "Next line here", "下一句"))
        val got = TranslationMatcher.attach(ours, theirs)
        assertEquals("我一直奔跑，穿过黑夜", got[0].translation)
        assertEquals("下一句", got[1].translation)
    }

    @Test fun keepsTranslationsAlreadyThereAndSkipsStrangers() {
        val ours = listOf(Line(1_000, "Hello there friend", translation = "你好朋友"), Line(5_000, "Something else entirely"))
        val theirs = listOf(SiteLine(1_000, "Hello there friend", "别的"), SiteLine(5_000, "Totally different words", "无关"))
        val got = TranslationMatcher.attach(ours, theirs)
        assertEquals("你好朋友", got[0].translation)      // not overwritten
        assertNull(got[1].translation)                  // words don't match: no translation
    }

    @Test fun repeatedChorusLinesShareTheirTranslation() {
        val got = TranslationMatcher.share(listOf(Line(1_000, "Stay with me", translation = "留下来陪我"),
            Line(9_000, "Something new"), Line(20_000, "Stay with me!")))
        assertEquals("留下来陪我", got[2].translation)
        assertNull(got[1].translation)
    }

    @Test fun decodesKugouKrcWithTranslation() {
        val tr = JSONObject().put("content", JSONArray().put(JSONObject().put("type", 1).put("lyricContent",
            JSONArray().put(JSONArray().put("你好")).put(JSONArray().put("世界")))))
        val b64 = java.util.Base64.getEncoder().encodeToString(tr.toString().toByteArray())
        val krc = "[language:$b64]\n[1000,2000]<0,500,0>Hello <500,500,0>you\n[4000,1500]<0,700,0>World"
        // pack it the way Kugou does: zlib, then XOR with the key, behind "krc1"
        val def = Deflater(); def.setInput(krc.toByteArray()); def.finish()
        val buf = ByteArray(4096); val n = def.deflate(buf)
        val key = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105)
        val raw = "krc1".toByteArray() + ByteArray(n) { i -> (buf[i].toInt() xor key[i % 16].toInt()).toByte() }
        val text = TranslationSources.decodeKrc(raw)!!
        val got = TranslationSources.parseKrc(text)
        assertEquals(listOf(SiteLine(1000, "Hello you", "你好"), SiteLine(4000, "World", "世界")), got)
    }

    @Test fun knowsWhichLinesNeedTranslating() {
        for (s in listOf("ohh ohh", "Yeah yeah yeah", "la-la-la", "shoo-be-doo-wop", "dum dum di-di-dum", "ラララ", "", "♪"))
            assertFalse("'$s' is only sounds", TextRules.worthTranslating(s))
        assertFalse(TextRules.worthTranslating("dead, dead, dead"))          // an echo
        assertFalse(TextRules.worthTranslating("作词 : 周杰伦"))
        assertFalse(TextRules.worthTranslating("我们的爱"))                    // already Chinese
        assertTrue(TextRules.worthTranslating("Oh, I'm in love"))
        assertTrue(TextRules.worthTranslating("夢ならばどれほどよかったでしょう"))
        assertTrue(TextRules.worthTranslating("未来", japaneseSong = true))     // kanji in a Japanese song
        assertTrue(TextRules.worthTranslating("mama, just killed a man"))
    }

    @Test fun tellsChineseSongsApart() {
        assertTrue(TextRules.isChineseSong(lines(0L to "天青色等烟雨", 1L to "而我在等你")))
        assertFalse(TextRules.isChineseSong(lines(0L to "夢ならばどれほど", 1L to "よかったでしょう")))
        assertFalse(TextRules.isChineseSong(lines(0L to "Shape of you")))
    }

    @Test fun tidiesRepeatedCharacters() {
        assertEquals("死", TextRules.tidy("死死死死"))
        assertEquals("哈哈哈", TextRules.tidy("哈哈哈"))
    }

    @Test fun readsClaudesAnswerAndRemembersIt() {
        val ai = AiTranslator(tmp.root)
        val got = ai.parse("""{"style":"pop","lines":[{"n":1,"zh":"你好"},{"n":7,"zh":"没问过"},{"n":2,"zh":" "}]}""", setOf(1, 2))
        assertEquals(mapOf(1 to "你好"), got)
        assertEquals(emptyMap<Int, String>(), ai.parse("not json", setOf(1)))
        assertEquals(AiTranslator.FEW_MODEL, AiTranslator.chooseModel(2, 40))
        assertEquals(AiTranslator.FULL_MODEL, AiTranslator.chooseModel(30, 40))
        assertEquals(0.0 to 0, ai.spent())
    }
}
