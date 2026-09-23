package com.adi.spamv4

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger

class ProxyRotator(rawProxies: List<String>) {

    data class Entry(
        val proxy: Proxy,
        val auth: Authenticator?
    )

    private val list: List<Entry> = rawProxies.mapNotNull { parse(it) }
    private val idx = AtomicInteger(0)

    fun size(): Int = list.size

    fun next(): Entry? {
        if (list.isEmpty()) return null
        return list[idx.getAndIncrement() % list.size]
    }

    companion object {

        fun parse(raw: String): Entry? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            return try {
                if (s.contains("@")) {
                    val parts = s.split("@", limit = 2)
                    val authPart = parts[0]
                    val hostPart = parts[1]
                    val authSplit = authPart.split(":", limit = 2)
                    val user = authSplit[0]
                    val pass = authSplit[1]
                    val hostSplit = hostPart.split(":", limit = 2)
                    val ip = hostSplit[0]
                    val port = hostSplit[1]
                    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(ip, port.toInt()))
                    val authenticator = object : Authenticator {
                        override fun authenticate(route: Route?, response: Response): Request? {
                            val cred = Credentials.basic(user, pass)
                            return response.request.newBuilder()
                                .header("Proxy-Authorization", cred)
                                .build()
                        }
                    }
                    Entry(proxy, authenticator)
                } else {
                    val parts = s.split(":", limit = 2)
                    val ip = parts[0]
                    val port = parts[1]
                    val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(ip, port.toInt()))
                    Entry(proxy, null)
                }
            } catch (e: Exception) { null }
        }
    }
}