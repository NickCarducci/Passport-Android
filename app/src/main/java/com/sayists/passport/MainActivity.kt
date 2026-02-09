package com.sayists.passport

//import com.google.firebase.Firebase
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException


data class Event(val title: String = "", val location: String = "", val date: String = "")

class MainActivity : AppCompatActivity() {

    private lateinit var leaderboardBtn: Button
    private lateinit var logOutBtn: Button
    private lateinit var scanQrBtn: Button
    private lateinit var scannedValueTv: TextView
    private var isScannerInstalled = false
    private lateinit var scanner: GmsBarcodeScanner
    //val db = Firebase.firestore
    private val client = OkHttpClient()
    val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        installGoogleScanner()
        initVars()
        registerUiListener()

        db.collection("events")
            //.whereEqualTo("state", "CA")
            .addSnapshotListener { value, exception ->
                if (exception != null) {
                    Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val descriptionLinks = ArrayList<String>()
                val eventTitles = ArrayList<String>()
                val events = ArrayList<Event>()
                //var descriptionLink = ""

                for (doc in value!!) {
                    val event = doc.toObject(Event::class.java)
                    events.add(event)
                    val date = doc.getString("date")
                    val title = doc.getString("title")
                    val location = doc.getString("location")
                    //descriptionLink = doc.getString("descriptionLink") ?: ""
                    descriptionLinks.add(doc.getString("descriptionLink") ?: "")
                    eventTitles.add(date + " " + title + ": " + location)

                }
                // access the listView from xml file
                var mListView = findViewById<ListView>(R.id.eventlist)
                val arrayAdapter: ArrayAdapter<*>
                arrayAdapter = ArrayAdapter(this,
                    android.R.layout.simple_list_item_1, eventTitles)
                mListView.setOnItemClickListener(OnItemClickListener { adapter, v, position, arg3 ->
                    val value = adapter.getItemAtPosition(position) as String
                    val url = descriptionLinks.get(position) as String
                    // assuming string and if you want to get the value on click of list item
                    // do what you intend to do on click of listview row
                    var intent = Intent(applicationContext, WebviewActivity::class.java)
                    intent.putExtra("url", url)

                    startActivity(intent)
                    finish()
                })
                mListView.adapter = arrayAdapter
                //Toast.makeText(this, "Current events at Monmouth U: $eventTitles", Toast.LENGTH_SHORT).show()
            }
    }
    private fun installGoogleScanner() {
        val moduleInstall = ModuleInstall.getClient(this)
        val moduleInstallRequest = ModuleInstallRequest.newBuilder()
            .addApi(GmsBarcodeScanning.getClient(this))
            .build()

        moduleInstall.installModules(moduleInstallRequest).addOnSuccessListener {
            isScannerInstalled = true
        }.addOnFailureListener {
            isScannerInstalled = false
            Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initVars() {
        leaderboardBtn = findViewById(R.id.leaderboardBtn)
        logOutBtn = findViewById(R.id.logOutBtn)
        scanQrBtn = findViewById(R.id.scanQrBtn)
        scannedValueTv = findViewById(R.id.scannedValueTv)
        scannedValueTv.text = "Scan a QR code"
        val options = initializeGoogleScanner()
        scanner = GmsBarcodeScanning.getClient(this, options)
    }

    private fun initializeGoogleScanner(): GmsBarcodeScannerOptions {
        return GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom().build()
    }

    private fun registerUiListener() {

        scanQrBtn.setOnClickListener {
            if (isScannerInstalled) {
                startScanning()
            } else {
                Toast.makeText(this, "Please try again...", Toast.LENGTH_SHORT).show()
            }
        }
        logOutBtn.setOnClickListener {
            FirebaseAuth.getInstance().signOut();
            startActivity(Intent(applicationContext, LoginActivity::class.java))
            finish()
        }
        leaderboardBtn.setOnClickListener {
            startActivity(Intent(applicationContext, LeaderboardActivity::class.java))
            finish()
        }
    }

    private fun startScanning() {
        scanner.startScan().addOnSuccessListener {
            val result = it.rawValue
            result?.let {
                attendEventViaApi(it)
            }
        }.addOnCanceledListener {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_SHORT).show()

        }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()

            }
    }

    private fun attendEventViaApi(eventId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val studentId = user.email?.substringBefore("@") ?: ""
        
        val json = JSONObject().apply {
            put("eventId", eventId)
            put("studentId", studentId)
            put("fullName", user.displayName ?: "")
            put("username", user.displayName ?: studentId)
            put("address", "") // To be populated once Profile view is added
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://starfish-app-x5itk.ondigitalocean.app/attend")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val responseData = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseData != null) {
                        val resJson = JSONObject(responseData)
                        val message = resJson.optString("message")
                        val title = resJson.optString("title")

                        if (message == "attended" || message == "already attended.") {
                            Toast.makeText(this@MainActivity, "$message $title", Toast.LENGTH_LONG).show()
                            val intent = Intent(applicationContext, ConfirmationActivity::class.java)
                            intent.putExtra("title", title)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Server Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}