package com.adi.spamv4

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class MemoryCookieJar : CookieJar {

    private val store = mutableMapOf<String, MutableList<Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val list = store.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { new ->
            list.removeAll { it.name == new.name }
            list.add(new)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host]?.filter { it.expiresAt > now } ?: emptyList()
    }

    fun inject(sessionId: String, csrf: String) {
        val exp = System.currentTimeMillis() + 30L * 24 * 3600 * 1000
        store["instagram.com"] = mutableListOf(
            Cookie.Builder().domain("instagram.com").path("/")
                .name("sessionid").value(sessionId).expiresAt(exp).build(),
            Cookie.Builder().domain("instagram.com").path("/")
                .name("csrftoken").value(csrf).expiresAt(exp).build()
        )
    }
}