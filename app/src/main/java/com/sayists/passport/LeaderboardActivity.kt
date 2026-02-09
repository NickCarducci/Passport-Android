package com.sayists.passport

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class LeaderboardActivity : AppCompatActivity() {
    val db = FirebaseFirestore.getInstance()
    var username : String =""
    private lateinit var backBtn: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        // start verification on click of the button
        findViewById<Button>(R.id.button_save).setOnClickListener {
            saveUsername()
        }
        backBtn = findViewById(R.id.backBtn)
        backBtn.setOnClickListener {
            startActivity(Intent(applicationContext, MainActivity::class.java))
            finish()
        }

        db.collection("leaders")
            //.whereEqualTo("state", "CA")
            .orderBy("eventsAttended", Query.Direction.DESCENDING)
            .addSnapshotListener { value, exception ->
                if (exception != null) {
                    Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val leaderNames = ArrayList<String>()

                for (doc in value!!) {
                    val username = doc.getString("username")
                    val eventsAttended = doc.getLong("eventsAttended")
                    leaderNames.add(""+username + ": " + eventsAttended)

                }
                // access the listView from xml file
                var mListView = findViewById<ListView>(R.id.leaderList)
                val arrayAdapter: ArrayAdapter<*>
                arrayAdapter = ArrayAdapter(this,
                    android.R.layout.simple_list_item_1, leaderNames)
                mListView.adapter = arrayAdapter
                //Toast.makeText(this, "Current leaders at Monmouth U: $leaderNames", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUsername() {
        username = findViewById<EditText>(R.id.et_username).text.trim().toString()
        // get the phone number from edit text and append the country cde with it
        if (username.isNotEmpty()){

            val user = FirebaseAuth.getInstance().currentUser
            user?.let {
                val leaderRef = db.collection("leaders").document(user.uid)

                // Set the "isCapital" field of the city 'DC'
                leaderRef
                    .update("username", username)
                    .addOnSuccessListener {
                        Log.d(
                            "user",
                            "DocumentSnapshot successfully updated!"
                        )
                    }
                    .addOnFailureListener { e -> Log.w("user", "Error updating document", e) }
            }
        }else{
            Toast.makeText(this,"Enter new username", Toast.LENGTH_SHORT).show()
        }
    }
}