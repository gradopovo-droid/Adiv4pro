package com.adi.spamv4

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ReportActivity : AppCompatActivity() {

    private val reasonLabels = listOf(
        "spam", "scam", "nudity", "hate", "bully", "vio",
        "selfharm", "false_info", "ip", "sale", "minor",
        "impersonation", "drugs", "weapons", "terrorism",
        "eating_disorder", "something_else"
    )

    private val selectedReasons = mutableSetOf<String>()
    private lateinit var tvLog: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var tvStatOk: TextView
    private lateinit var tvStatFail: TextView
    private lateinit var tvStatAcc: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        tvLog = findViewById(R.id.tvLog)
        logScroll = findViewById(R.id.logScroll)
        tvStatOk = findViewById(R.id.tvStatOk)
        tvStatFail = findViewById(R.id.tvStatFail)
        tvStatAcc = findViewById(R.id.tvStatAcc)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        val optionsContainer = findViewById<LinearLayout>(R.id.optionsContainer)
        val cbSelectAll = findViewById<CheckBox>(R.id.cbSelectAll)

        reasonLabels.forEach { key ->
            val cb = CheckBox(this).apply {
                text = key
                setTextColor(0xFFFFFFFF.toInt())
                buttonTintList = android.content.res.ColorStateList.valueOf(0xFFE91E63.toInt())
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedReasons.add(key) else selectedReasons.remove(key)
                }
            }
            optionsContainer.addView(cb)
        }

        cbSelectAll.setOnCheckedChangeListener { _, checked ->
            for (i in 0 until optionsContainer.childCount) {
                val v = optionsContainer.getChildAt(i)
                if (v is CheckBox) {
                    v.isChecked = checked
                    if (checked) selectedReasons.add(v.text.toString())
                    else selectedReasons.remove(v.text.toString())
                }
            }
        }

        ReportService.logListener = { line -> runOnUiThread { appendLog(line) } }
        ReportService.statsListener = { s -> runOnUiThread { updateStats(s) } }
        ReportService.deadListener = { runOnUiThread { onSessionDead() } }

        btnStart.setOnClickListener { startReport() }
        btnStop.setOnClickListener { stopReport() }

        if (ReportService.isRunning) {
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            appendLog("- service was already running -")
        }
    }

    private fun startReport() {
        val session = Prefs.load(this) ?: run {
            popup("Not Logged In", "Pehle login karo.")
            return
        }

        val target = findViewById<EditText>(R.id.etTarget).text.toString()
            .trim().removePrefix("@").removePrefix("https://instagram.com/").trimEnd('/')

        if (target.isEmpty()) { popup("No Target", "Target username daalo."); return }
        if (selectedReasons.isEmpty()) { popup("No Reasons", "Kam se kam ek reason select karo."); return }

        val sleepSec = findViewById<EditText>(R.id.etSleep).text.toString()
            .toLongOrNull()?.coerceIn(1, 600) ?: 5L
        val perAcc = findViewById<EditText>(R.id.etReportsPerAccount).text.toString()
            .toIntOrNull()?.coerceIn(1, 100) ?: 3

        val rawAccounts = findViewById<EditText>(R.id.etAccounts).text.toString()
        val rawProxies = findViewById<EditText>(R.id.etProxies).text.toString()

        val accounts = AccountStore.parse(rawAccounts)
        val proxies = rawProxies.lines().mapNotNull { ProxyRotator.parse(it) }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 69)
        }

        val config = ReportEngine.Config(
            targetUsername = target,
            reasonKeys = selectedReasons.toList(),
            sleepMs = sleepSec * 1000L,
            reportsPerAccount = perAcc,
            accounts = accounts,
            proxies = proxies,
            ownerSession = session
        )

        btnStart.isEnabled = false
        btnStop.isEnabled = true
        tvLog.text = ""
        appendLog("> starting ...")
        appendLog("  accounts: " + accounts.size)
        appendLog("  proxies: " + proxies.size)

        ReportService.start(this, config)
    }

    private fun stopReport() {
        ReportService.stop(this)
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        appendLog("- stopped by user")
    }

    private fun onSessionDead() {
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        Prefs.clear(this)
        popup("Session Problem",
            "IG ne session ya rate limit block kar diya. Login dobara karo.")
    }

    private fun popup(title: String, msg: String) {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle(title).setMessage(msg)
            .setPositiveButton("OK", null).show()
    }

    private fun appendLog(line: String) {
        tvLog.append("\n" + line)
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun updateStats(s: ReportEngine.Stats) {
        tvStatOk.text = "OK " + s.ok
        tvStatFail.text = "FAIL " + s.fail
        tvStatAcc.text = "ACC " + s.accUsed + "/" + s.accTotal
    }

    override fun onDestroy() {
        ReportService.logListener = null
        ReportService.statsListener = null
        ReportService.deadListener = null
        super.onDestroy()
    }
}