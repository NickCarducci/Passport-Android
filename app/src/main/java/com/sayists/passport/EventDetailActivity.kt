package com.sayists.passport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EventDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        val eventId = intent.getStringExtra("eventId") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val date = intent.getStringExtra("date") ?: ""
        val location = intent.getStringExtra("location") ?: ""

        findViewById<TextView>(R.id.eventTitleTv).text = title
        findViewById<TextView>(R.id.eventDateTv).text = date
        findViewById<TextView>(R.id.eventLocationTv).text = location

        // Check attendance status
        val attendanceStatusTv = findViewById<TextView>(R.id.attendanceStatusTv)
        val user = FirebaseAuth.getInstance().currentUser
        val studentId = user?.email?.split("@")?.firstOrNull() ?: ""
        if (studentId.isNotEmpty() && eventId.isNotEmpty()) {
            val db = FirebaseFirestore.getInstance()
            db.collection("events").document(eventId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val attendees = doc.get("attendees") as? List<*> ?: emptyList<Any>()
                        val hasAttended = attendees.contains(studentId)
                        attendanceStatusTv.text = if (hasAttended) {
                            "✓ Already Attended"
                        } else {
                            "Not Attended Yet"
                        }
                        attendanceStatusTv.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener {
                    attendanceStatusTv.visibility = View.GONE
                }
        } else {
            attendanceStatusTv.visibility = View.GONE
        }

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
