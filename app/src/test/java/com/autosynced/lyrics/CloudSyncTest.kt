package com.autosynced.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Runs on the PC: the file the PC app puts in the cloud is read the way it was meant. */
class CloudSyncTest {
    private fun gz(json: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    // the same shape cloud_sync.py writes
    private val file = gz("""{"v":1,"made":0,"songs":[
        {"ti":"Mine","ar":"Singer","d":185,"mine":true,"synced":true,"lines":[[1000,"my line",null,0]]},
        {"ti":"online song","ar":"band","d":200,"mine":false,"synced":true,
         "lines":[[1000,"hello","你好",0],[3500,"world","世界",1]]},
        {"ti":"mine","ar":"singer","d":185,"mine":false,"synced":true,"lines":[[0,"found online",null,0]]}]}""")

    @Test fun readsSongsAndTranslations() {
        val map = CloudSync.parse(file)
        val l = CloudSync.find(map, "Online Song", "Band", 201.0)!!
        assertEquals(listOf(1000L, 3500L), l.lines.map { it.timeMs })
        assertEquals("你好", l.lines[0].translation)
        assertEquals(false, l.lines[0].aiTranslated)
        assertTrue(l.lines[1].aiTranslated)
        assertEquals(CloudSync.SOURCE_FOUND, l.source)
    }

    @Test fun yourOwnLyricsWin() {
        val l = CloudSync.find(CloudSync.parse(file), "MINE", "singer", 0.0)!!
        assertEquals("my line", l.lines[0].text)
        assertEquals(CloudSync.SOURCE_MINE, l.source)
    }

    @Test fun anotherLengthIsAnotherVersion() {
        val map = CloudSync.parse(file)
        assertNull(CloudSync.find(map, "online song", "band", 210.0))      // found online: 3 s
        assertEquals("my line", CloudSync.find(map, "Mine", "Singer", 189.0)!!.lines[0].text)   // yours: 5 s
        assertNull(CloudSync.find(map, "Nope", "Singer", 0.0))
    }

    @Test fun ignoresAnUnknownFormat() {
        assertEquals(0, CloudSync.parse(gz("""{"v":2,"songs":[]}""")).size)
    }
}
