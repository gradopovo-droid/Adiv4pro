package com.adi.spamv4

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        var instance: MainActivity? = null
    }

    private lateinit var tvSession: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvSession = findViewById(R.id.tvSessionStatus)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenReporter).setOnClickListener {
            if (Prefs.load(this) == null) {
                popup("No Session", "Pehle Instagram login karo.")
            } else {
                startActivity(Intent(this, ReportActivity::class.java))
            }
        }

        findViewById<Button>(R.id.btnClearSession).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Session?")
                .setMessage("Saved session delete ho jayega.")
                .setPositiveButton("Yes") { _, _ ->
                    Prefs.clear(this)
                    refreshSession()
                }
                .setNegativeButton("No", null)
                .show()
        }

        refreshSession()
    }

    override fun onResume() {
        super.onResume()
        instance = this
        refreshSession()
    }

    override fun onStop() {
        super.onStop()
        if (instance == this) instance = null
    }

    private fun refreshSession() {
        val s = Prefs.load(this)
        tvSession.text = if (s == null) "no active session"
        else "logged in as @" + s.username + " ✓"
    }

    fun onLoginComplete(username: String, sessionId: String, csrf: String) {
        runOnUiThread {
            refreshSession()
            popup("Logged In", "session captured\n@$username")
        }
    }

    private fun popup(t: String, m: String) {
        AlertDialog.Builder(this).setTitle(t).setMessage(m)
            .setPositiveButton("OK", null).show()
    }
}