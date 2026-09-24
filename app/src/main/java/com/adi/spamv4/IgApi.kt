package com.adi.spamv4

import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class IgApi {

    private val cookieJar = MemoryCookieJar()

    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun clientWithProxy(proxyEntry: ProxyRotator.Entry?): OkHttpClient {
        val b = client.newBuilder()
        if (proxyEntry != null) {
            b.proxy(proxyEntry.proxy)
            if (proxyEntry.auth != null) b.proxyAuthenticator(proxyEntry.auth)
        }
        return b.build()
    }

    fun installSession(sessionId: String, csrf: String) {
        cookieJar.inject(sessionId, csrf)
    }

    data class LoginResult(val sessionId: String, val csrf: String, val userId: String)

    fun loginWithPassword(
        client: OkHttpClient,
        username: String,
        password: String
    ): LoginResult? {
        val csrf: String
        try {
            val primeReq = Request.Builder()
                .url("https://www.instagram.com/accounts/login/")
                .header("User-Agent", BROWSER_UA)
                .build()
            client.newCall(primeReq).execute().use { res ->
                csrf = res.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("csrftoken=") }
                    ?.substringAfter("csrftoken=")
                    ?.substringBefore(";") ?: return null
            }
        } catch (e: Exception) { return null }

        val ts = System.currentTimeMillis() / 1000
        val encPass = "#PWD_INSTAGRAM_BROWSER:0:" + ts + ":" + password

        val body = FormBody.Builder()
            .add("username", username)
            .add("enc_password", encPass)
            .add("queryParams", "{}")
            .add("optIntoOneTap", "false")
            .build()

        val loginReq = Request.Builder()
            .url("https://www.instagram.com/api/v1/web/accounts/login/ajax/")
            .post(body)
            .header("User-Agent", UA)
            .header("X-CSRFToken", csrf)
            .header("X-IG-App-ID", "936619743392459")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.instagram.com/accounts/login/")
            .header("Origin", "https://www.instagram.com")
            .build()

        return try {
            client.newCall(loginReq).execute().use { res ->
                val bodyStr = res.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                if (json.optBoolean("authenticated")) {
                    val uid = json.optString("userId")
                    val sid = res.headers("Set-Cookie")
                        .firstOrNull { it.startsWith("sessionid=") }
                        ?.substringAfter("sessionid=")
                        ?.substringBefore(";") ?: ""
                    if (sid.isEmpty()) return null
                    LoginResult(sid, csrf, uid)
                } else null
            }
        } catch (e: Exception) { null }
    }

    fun resolveUserId(client: OkHttpClient, username: String): String? = try {
        val req = Request.Builder()
            .url("https://www.instagram.com/api/v1/users/web_profile_info/?username=" + username)
            .header("User-Agent", UA)
            .header("X-IG-App-ID", "936619743392459")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val body = res.body?.string() ?: return null
            JSONObject(body)
                .getJSONObject("data")
                .getJSONObject("user")
                .getString("id")
        }
    } catch (e: Exception) { null }

    enum class ReportStatus { OK, FAIL, AUTH, RATE }

    fun reportUser(
        client: OkHttpClient,
        targetUserId: String,
        reasonId: String,
        csrf: String
    ): ReportStatus = try {
        val body = FormBody.Builder()
            .add("reason_id", reasonId)
            .add("source_name", "report_button")
            .build()

        val req = Request.Builder()
            .url("https://www.instagram.com/api/v1/users/" + targetUserId + "/report/")
            .post(body)
            .header("User-Agent", UA)
            .header("X-CSRFToken", csrf)
            .header("X-IG-App-ID", "936619743392459")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.instagram.com/")
            .header("Origin", "https://www.instagram.com")
            .build()

        client.newCall(req).execute().use { res ->
            when {
                res.isSuccessful -> ReportStatus.OK
                res.code == 401 || res.code == 403 -> ReportStatus.AUTH
                res.code == 429 -> ReportStatus.RATE
                else -> ReportStatus.FAIL
            }
        }
    } catch (e: Exception) { ReportStatus.FAIL }

    fun isSessionAlive(client: OkHttpClient): Boolean = try {
        val req = Request.Builder()
            .url("https://www.instagram.com/api/v1/users/web_profile_info/?username=instagram")
            .header("User-Agent", UA)
            .header("X-IG-App-ID", "936619743392459")
            .build()
        client.newCall(req).execute().use { res ->
            res.isSuccessful
        }
    } catch (e: Exception) { false }

    companion object {
        const val UA = "Instagram 269.0.0.18.75 Android (30/11; 320dpi; 720x1440; samsung; SM-A105F; a10; qcom; en_US; 458229237)"
        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}