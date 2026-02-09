package com.sayists.passport

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddressActivity : AppCompatActivity() {

    val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)

        // fill otp and call the on click on button
        findViewById<Button>(R.id.submit).setOnClickListener {
            val fullName = findViewById<EditText>(R.id.et_full_name).text.trim().toString()
            if(fullName.isNotEmpty()){
                val studentId = findViewById<EditText>(R.id.et_student_id).text.trim().toString()
                if(studentId.isNotEmpty()){
                    val addressLine1 = findViewById<EditText>(R.id.et_address_line_1).text.trim().toString()
                    if(addressLine1.isNotEmpty()){
                        val addressLine2 = findViewById<EditText>(R.id.et_address_line_2).text.trim().toString()
                        val city = findViewById<EditText>(R.id.et_city).text.trim().toString()
                        if(city.isNotEmpty()) {
                            val state = findViewById<EditText>(R.id.et_state).text.trim().toString()
                            if(state.isNotEmpty()) {
                                val zipCode = findViewById<EditText>(R.id.et_zip_code).text.trim().toString()
                                if(zipCode.isNotEmpty()) {

                                    val user = FirebaseAuth.getInstance().currentUser
                                    user?.let {
                                        /*val userData = hashMapOf(
                                            "address" to addressLine1
                                        )*/
                                        var address = ""
                                        if (addressLine2 != ""){
                                            address = addressLine1 + ", " + addressLine2 + ", " + city + ", " + state + " " + zipCode
                                        }else {
                                            address = addressLine1 + ", " + city + ", " + state + " " + zipCode
                                        }

                                        val userData = hashMapOf(
                                            "username" to "Student",
                                            "eventsAttended" to 0,
                                            "address" to address
                                            //"phone" to number,
                                        )

                                        db.collection("leaders").document(user.uid)
                                            .set(userData)
                                            .addOnSuccessListener {
                                                startActivity(
                                                    Intent(
                                                        applicationContext,
                                                        MainActivity::class.java
                                                    )
                                                )
                                                finish()
                                                Log.d("GFG", "DocumentSnapshot successfully written!") }
                                            .addOnFailureListener { e -> Log.w("GFG", "Error writing document", e) }
                                    }
                                }else{
                                    Toast.makeText(this,"Enter Zip Code", Toast.LENGTH_SHORT).show()
                                }
                            }else{
                                Toast.makeText(this,"Enter State", Toast.LENGTH_SHORT).show()
                            }
                        }else{
                            Toast.makeText(this,"Enter City", Toast.LENGTH_SHORT).show()
                        }
                    }else{
                        Toast.makeText(this,"Enter Address Line 1", Toast.LENGTH_SHORT).show()
                    }
                }else{
                    Toast.makeText(this,"Enter Student Id (s0989374)", Toast.LENGTH_SHORT).show()
                }
            }else{
                Toast.makeText(this,"Enter Full Name (Nicholas Matthew Carducci)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}