package com.adi.spamv4

import kotlinx.coroutines.*
import kotlin.math.max

class ReportEngine(private val api: IgApi) {

    val reasonMap = linkedMapOf(
        "spam"            to "1",
        "scam"            to "2",
        "nudity"          to "3",
        "hate"            to "4",
        "bully"           to "5",
        "vio"             to "6",
        "selfharm"        to "7",
        "false_info"      to "8",
        "ip"              to "9",
        "sale"            to "10",
        "minor"           to "11",
        "impersonation"   to "12",
        "drugs"           to "13",
        "weapons"         to "14",
        "terrorism"       to "15",
        "eating_disorder" to "16",
        "something_else"  to "0"
    )

    data class Config(
        val targetUsername: String,
        val reasonKeys: List<String>,
        val sleepMs: Long,
        val reportsPerAccount: Int,
        val accounts: List<Account>,
        val proxies: List<ProxyRotator.Entry>,
        val ownerSession: Prefs.Session
    )

    data class Stats(
        var ok: Int = 0,
        var fail: Int = 0,
        var auth: Int = 0,
        var accUsed: Int = 0,
        var accTotal: Int = 0
    )

    data class WorkerSession(
        val username: String,
        val sessionId: String,
        val csrf: String,
        val source: String
    )

    suspend fun run(
        config: Config,
        onLog: (String) -> Unit,
        onStats: (Stats) -> Unit,
        onSessionDead: () -> Unit
    ) = withContext(Dispatchers.IO) {

        val stats = Stats(accTotal = config.accounts.size + 1)
        onStats(stats)

        onLog("> target: @" + config.targetUsername)
        onLog("> accounts in pool: " + config.accounts.size)
        onLog("> proxies: " + config.proxies.size)

        val workers = mutableListOf<WorkerSession>()
        workers += WorkerSession(
            username = config.ownerSession.username.ifEmpty { "(owner)" },
            sessionId = config.ownerSession.sessionId,
            csrf = config.ownerSession.csrf,
            source = "owner"
        )

        for ((i, acc) in config.accounts.withIndex()) {
            ensureActive()
            val proxy = config.proxies.takeIf { it.isNotEmpty() }?.let { it[i % it.size] }
            val client = api.clientWithProxy(proxy)

            val session = if (acc.isSessionOnly) {
                WorkerSession(acc.username, acc.sessionId!!, acc.csrf!!, "pool-session")
            } else {
                onLog("> logging in " + acc.username + " ...")
                val res = api.loginWithPassword(client, acc.username, acc.password!!)
                if (res == null) {
                    onLog("  x login failed for " + acc.username)
                    stats.fail++
                    onStats(stats)
                    continue
                }
                WorkerSession(acc.username, res.sessionId, res.csrf, "pool-pass")
            }
            workers += session
            onLog("  ok " + acc.username + " ready")
            stats.accTotal = workers.size
            onStats(stats)
            delay(800L)
        }

        if (workers.isEmpty()) {
            onLog("x no working sessions")
            return@withContext
        }

        onLog("> resolving target id ...")
        var targetId: String? = null
        for (w in workers) {
            ensureActive()
            val client = api.clientWithProxy(pickProxy(config.proxies, 0))
            api.installSession(w.sessionId, w.csrf)
            targetId = api.resolveUserId(client, config.targetUsername)
            if (targetId != null) break
        }

        if (targetId == null) {
            onLog("x target not found or no session can resolve it")
            return@withContext
        }
        onLog("ok target id = " + targetId)

        val reasonIds = config.reasonKeys.mapNotNull { reasonMap[it] }
        if (reasonIds.isEmpty()) { onLog("x no valid reasons"); return@withContext }

        var proxyIdx = 0
        var workerIdx = 0
        var failsInARow = 0

        while (isActive) {
            val worker = workers[workerIdx % workers.size]
            val proxy = config.proxies.takeIf { it.isNotEmpty() }
                ?.let { it[proxyIdx % it.size] }
            val client = api.clientWithProxy(proxy)

            api.installSession(worker.sessionId, worker.csrf)

            repeat(config.reportsPerAccount) {
                ensureActive()

                val reason = reasonIds.random()
                val status = api.reportUser(client, targetId, reason, worker.csrf)

                when (status) {
                    IgApi.ReportStatus.OK -> {
                        stats.ok++
                        failsInARow = 0
                        onLog("[" + worker.username + "] reason=" + reason + " -> OK")
                    }
                    IgApi.ReportStatus.FAIL -> {
                        stats.fail++
                        failsInARow++
                        onLog("[" + worker.username + "] reason=" + reason + " -> FAIL")
                    }
                    IgApi.ReportStatus.AUTH -> {
                        stats.auth++
                        onLog("[" + worker.username + "] session dead - skip")
                        workerIdx++
                        stats.accUsed++
                        onStats(stats)
                        return@repeat
                    }
                    IgApi.ReportStatus.RATE -> {
                        stats.fail++
                        failsInARow++
                        onLog("[" + worker.username + "] rate limited")
                    }
                }

                onStats(stats)
                delay(max(1000L, config.sleepMs))
            }

            workerIdx++
            proxyIdx++
            stats.accUsed = workerIdx
            onStats(stats)

            if (failsInARow >= 8) {
                onLog("8 consecutive fails - IG blocking. stopping.")
                onSessionDead()
                return@withContext
            }

            if (workerIdx % workers.size == 0) {
                onLog("- full rotation done, cooldown 30s -")
                delay(30_000L)
            }
        }
    }

    private fun pickProxy(list: List<ProxyRotator.Entry>, i: Int): ProxyRotator.Entry? {
        if (list.isEmpty()) return null
        return list[i % list.size]
    }
}