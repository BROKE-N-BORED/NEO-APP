package com.brokenbored.neo.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API keys, encrypted on the phone. The user enters them in Settings; they
 * are never hardcoded and never leave the device except to the provider
 * they belong to.
 */
class KeyStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "neo_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Returns the stored key for a provider id ("groq", "gemini", "exaone"),
     * or null when none has been entered. */
    fun getKey(providerId: String): String? =
        prefs.getString(providerId, null)?.takeIf { it.isNotBlank() }

    fun setKey(providerId: String, key: String) {
        prefs.edit().putString(providerId, key.trim()).apply()
    }

    fun clearKey(providerId: String) {
        prefs.edit().remove(providerId).apply()
    }

    fun hasKey(providerId: String): Boolean = getKey(providerId) != null
}
