package com.autosynced.lyrics

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs on the PC: every language file reads, and no translation lost a {0} or %.1f. */
class LanguageFilesTest {
    @Test fun languageFilesAreComplete() {
        val dir = File("src/main/assets/i18n")
        if (!dir.exists()) return                       // not run from the app module
        for (code in Lang.LANGUAGES.map { it.first }.filter { it != Lang.EN && it != Lang.ZH }) {
            val j = JSONObject(File(dir, "$code.json").readText())
            assertTrue("$code is nearly empty", j.length() > 250)
            for (en in j.keys()) for (ph in listOf("{0}", "{1}", "%.1f")) {
                if (ph in en) assertTrue("$code lost $ph in: $en", ph in j.getString(en))
            }
        }
    }
}
