package com.sayists.passport

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class ConfirmationActivity : AppCompatActivity() {
    private lateinit var ConfirmedTv: TextView
    val db = FirebaseFirestore.getInstance()
    // get reference of the firebase auth
    lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_confirmation)

        ConfirmedTv = findViewById(R.id.ConfirmedTv)


        val intent = intent
        val title = intent.getStringExtra("title") ?: "event!"
        ConfirmedTv.text = "Checked into " + title

        // fill otp and call the on click on button
        findViewById<Button>(R.id.home).setOnClickListener {
            startActivity(
                Intent(
                    applicationContext,
                    MainActivity::class.java
                )
            )
            finish()
        }
    }
}