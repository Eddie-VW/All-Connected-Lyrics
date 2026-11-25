package com.autosynced.lyrics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Album covers you picked yourself: one per album (every song on it by the same main artist),
 * or, for a song with no album name, one for that song ([CoverKey]). They're used instead of the
 * music app's cover everywhere: main screen, lock screen, backgrounds and colours.
 */
object CustomCovers {
    private var dir: File? = null
    private val cache = HashMap<String, Bitmap?>()

    fun init(c: Context) { dir = File(c.filesDir, "covers") }

    private fun file(key: String) = dir?.let { File(it, "$key.jpg") }

    /** The custom cover for this song, or null (read once, then kept in memory). */
    fun get(title: String, artist: String, album: String): Bitmap? {
        val key = CoverKey.of(title, artist, album)
        return cache.getOrPut(key) {
            file(key)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        }
    }

    fun has(title: String, artist: String, album: String) = file(CoverKey.of(title, artist, album))?.exists() == true

    /** Saves the picture at [uri] (shrunk to at most 1024 px) as this song's / album's cover. False if it isn't a picture. */
    fun set(c: Context, uri: Uri, title: String, artist: String, album: String): Boolean {
        val key = CoverKey.of(title, artist, album)
        val target = file(key) ?: return false
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
            val bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return false
            target.parentFile?.mkdirs()
            target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            cache[key] = bmp
            true
        } catch (_: Exception) { false }
    }

    fun remove(title: String, artist: String, album: String) {
        val key = CoverKey.of(title, artist, album)
        file(key)?.delete()
        cache[key] = null
    }
}
