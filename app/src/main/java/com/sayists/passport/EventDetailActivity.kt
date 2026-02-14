package com.sayists.passport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class EventDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        val title = intent.getStringExtra("title") ?: ""
        val date = intent.getStringExtra("date") ?: ""
        val location = intent.getStringExtra("location") ?: ""

        findViewById<TextView>(R.id.eventTitleTv).text = title
        findViewById<TextView>(R.id.eventDateTv).text = date
        findViewById<TextView>(R.id.eventLocationTv).text = location

        // Open directions in Google Maps
        findViewById<Button>(R.id.getDirectionsBtn).setOnClickListener {
            if (location.isNotEmpty()) {
                val uri = Uri.parse("geo:0,0?q=${Uri.encode(location)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    // Fallback to browser if Google Maps not installed
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(location)}")
                    )
                    startActivity(browserIntent)
                }
            }
        }

        findViewById<Button>(R.id.backBtn).setOnClickListener {
            finish()
        }
    }
}
