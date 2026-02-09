package com.sayists.passport

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.appcheck.appCheck
import com.google.firebase.firestore.FirebaseFirestore

class PhoneNumberActivity : AppCompatActivity() {
    val db = FirebaseFirestore.getInstance()

    // this stores the phone number of the user
    var number : String =""
    // create instance of firebase auth
    lateinit var auth: FirebaseAuth
    // we will use this to match the sent otp from firebase
    lateinit var storedVerificationId:String
    lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_number)


        auth=FirebaseAuth.getInstance()
        init()

        // start verification on click of the button
        findViewById<Button>(R.id.button_otp).setOnClickListener {
            login()
        }

        // Callback function for Phone Auth
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            // This method is called when the verification is completed
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                /*val user = FirebaseAuth.getInstance().currentUser
                user?.let{
                    val docRef = db.collection("leaders").document(user.uid)
                    docRef.get()
                        .addOnSuccessListener { document ->
                            if (document != null) {
                                startActivity(Intent(applicationContext, MainActivity::class.java))
                                finish()
                                Log.d("GFG", "DocumentSnapshot data: ${document.data}")
                            } else {
                                startActivity(Intent(applicationContext, AddressActivity::class.java))
                                finish()
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.d("GFG", "get failed with ", exception)
                        }
                }*/
                Log.d("GFG" , "onVerificationCompleted Success")
            }

            // Called when verification is failed add log statement to see the exception
            override fun onVerificationFailed(e: FirebaseException) {
                Log.d("GFG" , "onVerificationFailed  $e")
            }

            // On code is sent by the firebase this method is called
            // in here we start a new activity where user can enter the OTP
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d("GFG","onCodeSent: $verificationId")
                storedVerificationId = verificationId
                resendToken = token
                // Start a new activity using intent
                // also send the storedVerificationId using intent
                // we will use this id to send the otp back to firebase
                val intent = Intent(applicationContext,OtpActivity::class.java)
                intent.putExtra("storedVerificationId",storedVerificationId)
                startActivity(intent)
                finish()
            }
        }
        /*FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if(user != null){
                // User is signed in
                Log.d("auth", "Signed in")
                Toast.makeText(this, "User", Toast.LENGTH_LONG).show();

                startActivity(Intent(applicationContext, MainActivity::class.java))
                finish()
            }else{
                // User is signed out
                Log.d("auth", "Signed out")
                Toast.makeText(this, "Null", Toast.LENGTH_LONG).show();
            }
        }*/
    }
    private fun init() {
        // [START appcheck_initialize]
        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
        // [END appcheck_initialize]
    }

    private fun login() {
        number = findViewById<EditText>(R.id.et_phone_number).text.trim().toString()
        // get the phone number from edit text and append the country cde with it
        if (number.isNotEmpty()){
            number = "+$number"
            sendVerificationCode(number)
        }else{
            Toast.makeText(this,"Enter mobile number", Toast.LENGTH_SHORT).show()
        }
    }

    // this method sends the verification code and starts the callback of verification
    // which is implemented above in onCreate
    private fun sendVerificationCode(number: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(number) // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(this) // Activity (for callback binding)
            .setCallbacks(callbacks) // OnVerificationStateChangedCallbacks
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        Log.d("GFG" , "Auth started")
    }

}