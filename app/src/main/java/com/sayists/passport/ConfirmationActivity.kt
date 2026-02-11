package com.sayists.passport

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class ConfirmationActivity : AppCompatActivity() {
    private lateinit var confirmedTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirmation)

        confirmedTv = findViewById(R.id.ConfirmedTv)

        val title = intent.getStringExtra("title") ?: "event!"
        confirmedTv.text = "Checked into $title"

        findViewById<Button>(R.id.home).setOnClickListener {
            startActivity(Intent(applicationContext, MainActivity::class.java))
            finish()
        }
    }
}
