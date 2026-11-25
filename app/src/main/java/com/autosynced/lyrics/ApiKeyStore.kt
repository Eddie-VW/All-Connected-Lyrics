package com.autosynced.lyrics

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's Claude API key (from console.anthropic.com), for AI translation.
 * Stored encrypted with a key that lives in Android's Keystore and never leaves this phone
 * (like the PC app's DPAPI-protected api_key.bin). A copied or restored backup can't decrypt
 * it, so after moving phones the key just has to be entered again.
 */
object ApiKeyStore {
    private const val ALIAS = "claude_api_key"
    private const val PREFS = "secure"
    private const val ENTRY = "api_key"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return gen.generateKey()
    }

    fun has(c: Context): Boolean = prefs(c).contains(ENTRY)

    fun get(c: Context): String? {
        val stored = prefs(c).getString(ENTRY, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, raw, 0, 12))
            String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8).ifEmpty { null }
        } catch (_: Exception) {
            null                          // e.g. restored from another phone: ask for the key again
        }
    }

    /** Saves [key]; blank removes it. */
    fun set(c: Context, key: String?) {
        val k = key?.trim().orEmpty()
        if (k.isEmpty()) { prefs(c).edit().remove(ENTRY).apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val out = cipher.iv + cipher.doFinal(k.toByteArray(Charsets.UTF_8))
        prefs(c).edit().putString(ENTRY, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }
}
