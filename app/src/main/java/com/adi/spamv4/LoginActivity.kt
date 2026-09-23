package com.adi.spamv4

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private var capturedSession: String? = null
    private var capturedCsrf: String? = null
    private var popupShown = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val web = findViewById<WebView>(R.id.webview)
        val progress = findViewById<android.widget.ProgressBar>(R.id.progress)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = IgApi.UA
        }

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = android.view.View.VISIBLE
                if (url == null) return

                cm.getCookie(url)?.split(";")?.forEach { c ->
                    val t = c.trim()
                    if (t.startsWith("sessionid=")) capturedSession = t.substringAfter("sessionid=")
                    if (t.startsWith("csrftoken=")) capturedCsrf = t.substringAfter("csrftoken=")
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = android.view.View.GONE
                if (url == null) return

                if (url == "https://www.instagram.com/" &&
                    capturedSession != null && capturedCsrf != null
                ) {
                    finishSuccess()
                    return
                }

                if (url.contains("/accounts/login")) {
                    view?.evaluateJavascript(
                        "(function(){var e=document.querySelector('#slfErrorAlert,[data-testid=\"login-error-message\"],._ab2z');return e?e.innerText:'';})();"
                    ) { r ->
                        val txt = (r ?: "").lowercase()
                        if (!popupShown && (
                                    txt.contains("password") || txt.contains("incorrect") ||
                                            txt.contains("username") || txt.contains("sorry") ||
                                            txt.contains("challenge") || txt.contains("suspended")
                                    )
                        ) {
                            popupShown = true
                            showErr("Login Failed",
                                "Username ya password galat hai, ya IG ne challenge bheja. dobara try karo.")
                        }
                    }
                }
            }
        }

        web.loadUrl("https://www.instagram.com/accounts/login/")
    }

    private fun showErr(title: String, msg: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun finishSuccess() {
        val user = intent.getStringExtra("username") ?: ""
        MainActivity.instance?.onLoginComplete(user, capturedSession!!, capturedCsrf!!)
        finish()
    }
}