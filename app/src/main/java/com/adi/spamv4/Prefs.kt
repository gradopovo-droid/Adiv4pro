package com.adi.spamv4

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {

    data class Session(
        val username: String,
        val sessionId: String,
        val csrf: String,
        val userId: String = ""
    )

    private const val FILE = "adispam_secure"
    private const val K_USER = "u"
    private const val K_SID = "s"
    private const val K_CSRF = "c"
    private const val K_UID = "i"

    private fun sp(ctx: Context) = EncryptedSharedPreferences.create(
        ctx,
        FILE,
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(ctx: Context, s: Session) {
        sp(ctx).edit()
            .putString(K_USER, s.username)
            .putString(K_SID, s.sessionId)
            .putString(K_CSRF, s.csrf)
            .putString(K_UID, s.userId)
            .apply()
    }

    fun load(ctx: Context): Session? {
        val p = sp(ctx)
        val u = p.getString(K_USER, null) ?: return null
        val sid = p.getString(K_SID, null) ?: return null
        val csrf = p.getString(K_CSRF, null) ?: return null
        val uid = p.getString(K_UID, "") ?: ""
        if (sid.isEmpty() || csrf.isEmpty()) return null
        return Session(u, sid, csrf, uid)
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }
}