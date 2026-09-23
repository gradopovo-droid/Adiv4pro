package com.adi.spamv4

data class Account(
    val username: String,
    val password: String?,
    val sessionId: String?,
    val csrf: String? = null
) {
    val isSessionOnly: Boolean get() = sessionId != null && csrf != null
}

object AccountStore {

    fun parse(block: String): List<Account> {
        return block.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(":")
                when {
                    parts.size == 2 -> Account(
                        username = parts[0].trim(),
                        password = parts[1].trim(),
                        sessionId = null
                    )
                    parts.size >= 3 -> {
                        val u = parts[0].trim()
                        val a = parts[1].trim()
                        val b = parts[2].trim()
                        if (b.length in 28..40 && a.length > 40) {
                            Account(u, null, a, b)
                        } else {
                            Account(u, a, null)
                        }
                    }
                    else -> null
                }
            }
    }
}