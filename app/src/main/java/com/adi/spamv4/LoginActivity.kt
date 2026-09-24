package com.adi.spamv4

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build UI programmatically — no XML needed
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0B0F.toInt())
            setPadding(50, 80, 50, 50)
        }

        val title = TextView(this).apply {
            text = "Login to Instagram"
            setTextColor(0xFFE91E63.toInt())
            textSize = 22f
            setPadding(0, 0, 0, 30)
        }

        val etUser = EditText(this).apply {
            hint = "username"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE91E63.toInt())
        }

        val etPass = EditText(this).apply {
            hint = "password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE91E63.toInt())
        }

        val btn = Button(this).apply {
            text = "LOGIN"
            setBackgroundColor(0xFFE91E63.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }

        val progress = ProgressBar(this).apply {
            visibility = View.GONE
        }

        val tvStatus = TextView(this).apply {
            setTextColor(0xFF7CFC00.toInt())
            textSize = 12f
            setPadding(0, 20, 0, 0)
        }

        root.addView(title)
        root.addView(etUser)
        root.addView(etPass)
        root.addView(btn)
        root.addView(progress)
        root.addView(tvStatus)
        setContentView(root)

        btn.setOnClickListener {
            val user = etUser.text.toString().trim()
            val pass = etPass.text.toString()

            if (user.isEmpty() || pass.isEmpty()) {
                popup("Missing Fields", "Username aur password dono daalo.")
                return@setOnClickListener
            }

            btn.isEnabled = false
            progress.visibility = View.VISIBLE
            tvStatus.text = "logging in..."

            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    val api = IgApi()
                    api.loginWithPassword(api.client, user, pass)
                }

                progress.visibility = View.GONE
                btn.isEnabled = true

                if (result != null) {
                    // save session
                    Prefs.save(
                        this@LoginActivity,
                        Prefs.Session(user, result.sessionId, result.csrf, result.userId)
                    )
                    tvStatus.text = "logged in as @$user"
                    MainActivity.instance?.onLoginComplete(user, result.sessionId, result.csrf)
                    finish()
                } else {
                    tvStatus.text = ""
                    popup("Login Failed",
                        "Username ya password galat hai, ya IG ne challenge bheja.\n\n" +
                        "Agar 2FA on hai to webview waala version use karo.")
                }
            }
        }
    }

    private fun popup(title: String, msg: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }
}