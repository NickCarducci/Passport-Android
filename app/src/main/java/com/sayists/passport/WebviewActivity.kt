package com.sayists.passport

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button

class WebviewActivity : AppCompatActivity() {

    private lateinit var backBtn: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val intent = intent
        val url = intent.getStringExtra("url") ?: ""
        val myWebView: WebView = findViewById(R.id.webview)
        myWebView.loadUrl(url)

        backBtn = findViewById(R.id.backBtn)
        backBtn.setOnClickListener {
            startActivity(Intent(applicationContext, MainActivity::class.java))
            finish()
        }
    }
}