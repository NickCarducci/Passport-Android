package com.sayists.passport

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.AbsListView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

// data class Event(val title: String = "", val location: String = "", val date: String = "")

/**
 * Focal Architecture: Single activity with 4 panels navigated by gestures.
 *
 * Layout (matching iOS ContentView):
 *   Profile  <--swipe-->  Events (hub)  <--swipe-->  Leaderboard
 *                            |
 *                       pull up/down
 *                            |
 *                         Scanner
 */
class MainActivity : AppCompatActivity() {

    // --- Navigation state ---
    enum class ViewMode { LIST, PROFILE, LEADERBOARD, SCANNER }
    enum class DragDirection { NONE, HORIZONTAL, VERTICAL }

    private var currentMode = ViewMode.LIST
    private var dragDirection = DragDirection.NONE
    private var gestureStarted = false
    private var isDragIntercepted = false
    private var cancelSentToChildren = false
    private var horizontalOffset = 0f
    private var verticalOffset = 0f
    private var startX = 0f
    private var startY = 0f
    private var isAtBottom = false
    private var touchSlop = 0
    private var isAnimating = false

    // Gesture state manager (tap-through prevention)
    private var isDragging = false
    private var lastGestureEndTime = 0L

    // --- View references ---
    private lateinit var profilePanel: View
    private lateinit var eventsPanel: View
    private lateinit var leaderboardPanel: View
    private lateinit var scannerPanel: View
    private lateinit var floatingNavBtn: TextView

    // --- Scanner ---
    private lateinit var scanner: GmsBarcodeScanner

    // --- Data ---
    private val db = FirebaseFirestore.getInstance()
    private val descriptionLinks = ArrayList<String>()

    // ========================================================================
    // Lifecycle
    // ========================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        // Panels
        profilePanel = findViewById(R.id.profileView)
        eventsPanel = findViewById(R.id.eventsView)
        leaderboardPanel = findViewById(R.id.leaderboardView)
        scannerPanel = findViewById(R.id.scannerView)
        floatingNavBtn = findViewById(R.id.floatingNavBtn)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.focalContainer)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Scanner setup
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        scanner = GmsBarcodeScanning.getClient(this, options)

        val moduleInstallRequest = ModuleInstallRequest.newBuilder()
            .addApi(GmsBarcodeScanning.getClient(this))
            .build()
        ModuleInstall.getClient(this).installModules(moduleInstallRequest)

        // Back button (modern API)
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentMode != ViewMode.LIST) {
                        animateToMode(ViewMode.LIST)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        setupEventsList()
        setupLeaderboard()
        setupProfile()
        setupScanner()
        setupNavButtons()

        // Initial positioning (no animation)
        positionViewsImmediate(ViewMode.LIST)
    }

    // ========================================================================
    // Gesture Handling — Focal Architecture
    // ========================================================================

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isAnimating) return super.dispatchTouchEvent(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX
                startY = ev.rawY
                gestureStarted = false
                isDragIntercepted = false
                cancelSentToChildren = false
                dragDirection = DragDirection.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragIntercepted) {
                    val dx = ev.rawX - startX
                    val dy = ev.rawY - startY

                    // Jitter-resistant latch: wait for meaningful movement (> touchSlop)
                    if (!gestureStarted && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        gestureStarted = true

                        // Determine dominant direction and whether to intercept
                        if (abs(dx) > abs(dy)) {
                            dragDirection = DragDirection.HORIZONTAL
                            isDragIntercepted = when (currentMode) {
                                ViewMode.LIST -> true
                                ViewMode.PROFILE -> dx < 0
                                ViewMode.LEADERBOARD -> dx > 0
                                ViewMode.SCANNER -> false
                            }
                        } else {
                            dragDirection = DragDirection.VERTICAL
                            isDragIntercepted = when (currentMode) {
                                ViewMode.LIST -> isAtBottom && dy < 0
                                ViewMode.SCANNER -> dy > 0
                                else -> false
                            }
                        }

                        if (isDragIntercepted) {
                            isDragging = true
                        }
                    }
                }

                if (isDragIntercepted) {
                    // Send cancel to children so ListView stops scrolling
                    if (!cancelSentToChildren) {
                        cancelSentToChildren = true
                        val cancel = MotionEvent.obtain(ev)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                    }

                    val dx = ev.rawX - startX
                    val dy = ev.rawY - startY

                    when (dragDirection) {
                        DragDirection.HORIZONTAL -> {
                            horizontalOffset = dx
                            verticalOffset = 0f
                        }
                        DragDirection.VERTICAL -> {
                            horizontalOffset = 0f
                            verticalOffset = dy
                        }
                        DragDirection.NONE -> {}
                    }

                    updateViewTranslations()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragIntercepted) {
                    val dx = ev.rawX - startX
                    val dy = ev.rawY - startY

                    var newMode = currentMode

                    if (dragDirection == DragDirection.HORIZONTAL) {
                        when (currentMode) {
                            ViewMode.LIST -> {
                                if (dx < -100) newMode = ViewMode.LEADERBOARD
                                else if (dx > 100) newMode = ViewMode.PROFILE
                            }
                            ViewMode.PROFILE -> {
                                if (dx < -100) newMode = ViewMode.LIST
                            }
                            ViewMode.LEADERBOARD -> {
                                if (dx > 100) newMode = ViewMode.LIST
                            }
                            else -> {}
                        }
                    } else if (dragDirection == DragDirection.VERTICAL) {
                        when (currentMode) {
                            ViewMode.LIST -> {
                                if (isAtBottom && dy < -100) newMode = ViewMode.SCANNER
                            }
                            ViewMode.SCANNER -> {
                                if (dy > 100) newMode = ViewMode.LIST
                            }
                            else -> {}
                        }
                    }

                    endDrag(didNavigate = newMode != currentMode)
                    animateToMode(newMode)
                    return true
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    // ========================================================================
    // View Positioning — Matching iOS offset logic
    // ========================================================================

    /**
     * Translates all 4 panels based on [currentMode] + drag offsets.
     *
     * iOS mapping:
     *   profileView.x  = (mode==profile ? 0 : -sw) + hOffset
     *   listView.x     = (mode==profile ? sw : mode==leaderboard ? -sw : 0) + hOffset
     *   listView.y     = (mode==scanner ? -sh : 0) + vOffset
     *   leaderboard.x  = (mode==leaderboard ? 0 : sw) + hOffset
     *   scanner.y      = (mode==scanner ? 0 : sh) + vOffset
     */
    private fun updateViewTranslations() {
        val sw = resources.displayMetrics.widthPixels.toFloat()
        val sh = resources.displayMetrics.heightPixels.toFloat()

        profilePanel.translationX =
            (if (currentMode == ViewMode.PROFILE) 0f else -sw) + horizontalOffset

        eventsPanel.translationX = when (currentMode) {
            ViewMode.PROFILE -> sw
            ViewMode.LEADERBOARD -> -sw
            else -> 0f
        } + horizontalOffset

        eventsPanel.translationY =
            (if (currentMode == ViewMode.SCANNER) -sh else 0f) + verticalOffset

        leaderboardPanel.translationX =
            (if (currentMode == ViewMode.LEADERBOARD) 0f else sw) + horizontalOffset

        scannerPanel.translationY =
            (if (currentMode == ViewMode.SCANNER) 0f else sh) + verticalOffset
    }

    private fun positionViewsImmediate(mode: ViewMode = ViewMode.LIST) {
        currentMode = mode
        horizontalOffset = 0f
        verticalOffset = 0f
        updateViewTranslations()
        updateFloatingNav()
    }

    private fun animateToMode(newMode: ViewMode) {
        // Capture current pixel positions
        data class Pos(val tx: Float, val ty: Float)

        val starts = mapOf(
            profilePanel to Pos(profilePanel.translationX, profilePanel.translationY),
            eventsPanel to Pos(eventsPanel.translationX, eventsPanel.translationY),
            leaderboardPanel to Pos(leaderboardPanel.translationX, leaderboardPanel.translationY),
            scannerPanel to Pos(scannerPanel.translationX, scannerPanel.translationY)
        )

        // Calculate end positions
        currentMode = newMode
        horizontalOffset = 0f
        verticalOffset = 0f
        updateViewTranslations()

        val ends = mapOf(
            profilePanel to Pos(profilePanel.translationX, profilePanel.translationY),
            eventsPanel to Pos(eventsPanel.translationX, eventsPanel.translationY),
            leaderboardPanel to Pos(leaderboardPanel.translationX, leaderboardPanel.translationY),
            scannerPanel to Pos(scannerPanel.translationX, scannerPanel.translationY)
        )

        // Animate from start to end
        isAnimating = true
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                for (view in listOf(profilePanel, eventsPanel, leaderboardPanel, scannerPanel)) {
                    val s = starts[view]!!
                    val e = ends[view]!!
                    view.translationX = s.tx + (e.tx - s.tx) * p
                    view.translationY = s.ty + (e.ty - s.ty) * p
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    updateFloatingNav()

                    if (newMode == ViewMode.SCANNER) {
                        launchScanner()
                    }
                }
            })
            start()
        }
    }

    // ========================================================================
    // Tap-Through Prevention (GestureStateManager equivalent)
    // ========================================================================

    private fun endDrag(@Suppress("UNUSED_PARAMETER") didNavigate: Boolean){
        isDragging = false
        lastGestureEndTime = System.currentTimeMillis()
    }

    private fun shouldBlockTaps(): Boolean {
        if (isDragging) return true
        val elapsed = System.currentTimeMillis() - lastGestureEndTime
        return elapsed < 400
    }

    // ========================================================================
    // Floating Nav Button (iOS backButton equivalent)
    // ========================================================================

    private fun updateFloatingNav() {
        when (currentMode) {
            ViewMode.SCANNER -> {
                floatingNavBtn.visibility = View.GONE
            }
            ViewMode.LIST -> {
                floatingNavBtn.visibility = View.VISIBLE
                floatingNavBtn.text = getString(R.string.footer_scan)
            }
            else -> {
                floatingNavBtn.visibility = View.VISIBLE
                floatingNavBtn.text = getString(R.string.footer_back)
            }
        }
    }

    // ========================================================================
    // Setup
    // ========================================================================

    private fun setupNavButtons() {
        // Events header: profile / leaderboard
        findViewById<ImageButton>(R.id.profileNavBtn).setOnClickListener {
            if (!shouldBlockTaps()) animateToMode(ViewMode.PROFILE)
        }
        findViewById<ImageButton>(R.id.leaderboardNavBtn).setOnClickListener {
            if (!shouldBlockTaps()) animateToMode(ViewMode.LEADERBOARD)
        }

        // Scanner: back to list
        findViewById<ImageButton>(R.id.listNavBtn).setOnClickListener {
            if (!shouldBlockTaps()) animateToMode(ViewMode.LIST)
        }

        // Floating nav (scan / back)
        floatingNavBtn.setOnClickListener {
            if (shouldBlockTaps()) return@setOnClickListener
            when (currentMode) {
                ViewMode.LIST -> animateToMode(ViewMode.SCANNER)
                else -> animateToMode(ViewMode.LIST)
            }
        }
    }

    // --- Events List ---

    private fun setupEventsList() {
        val eventList = findViewById<ListView>(R.id.eventlist)

        // Scroll position tracking for pull-up detection
        eventList.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScroll(
                view: AbsListView, firstVisible: Int, visibleCount: Int, totalCount: Int
            ) {
                isAtBottom = totalCount > 0 && (firstVisible + visibleCount >= totalCount)
            }
            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}
        })

        eventList.onItemClickListener = OnItemClickListener { _, _, position, _ ->
            if (shouldBlockTaps()) return@OnItemClickListener
            if (position < descriptionLinks.size) {
                val url = descriptionLinks[position]
                if (url.isNotEmpty()) {
                    val intent = Intent(applicationContext, WebviewActivity::class.java)
                    intent.putExtra("url", url)
                    startActivity(intent)
                }
            }
        }

        loadEvents()
    }

    private fun loadEvents() {
        db.collection("events")
            .addSnapshotListener { value, exception ->
                if (exception != null) {
                    Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                descriptionLinks.clear()
                val eventTitles = ArrayList<String>()

                for (doc in value!!) {
                    val date = doc.getString("date")
                    val title = doc.getString("title")
                    val location = doc.getString("location")
                    descriptionLinks.add(doc.getString("descriptionLink") ?: "")
                    eventTitles.add("$date $title: $location")
                }

                val mListView = findViewById<ListView>(R.id.eventlist)
                mListView.adapter = ArrayAdapter(
                    this, android.R.layout.simple_list_item_1, eventTitles
                )
            }
    }

    // --- Leaderboard ---

    private fun setupLeaderboard() {
        db.collection("leaders")
            .orderBy("eventsAttended", Query.Direction.DESCENDING)
            .addSnapshotListener { value, exception ->
                if (exception != null) {
                    Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val leaderNames = ArrayList<String>()
                for (doc in value!!) {
                    val studentId = doc.id
                    val username = doc.getString("username")
                    val eventsAttended = doc.getLong("eventsAttended")
                    val displayName = if (!username.isNullOrEmpty()) username else "Anonymous"
                    leaderNames.add("$displayName: $eventsAttended")
                }

                val mListView = findViewById<ListView>(R.id.leaderList)
                mListView.adapter = ArrayAdapter(
                    this, android.R.layout.simple_list_item_1, leaderNames
                )
            }
    }

    // --- Profile ---

    private fun setupProfile() {
        val user = FirebaseAuth.getInstance().currentUser
        val studentId = user?.email?.substringBefore("@") ?: ""
        findViewById<TextView>(R.id.studentIdTv).text =
            studentId.ifEmpty { "Not signed in" }

        val usernameEt = findViewById<EditText>(R.id.usernameEt)

        // Load current username from Firestore
        if (studentId.isNotEmpty()) {
            db.collection("leaders").document(studentId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        usernameEt.setText(doc.getString("username") ?: "")
                    }
                }
        }

        // Save username
        findViewById<Button>(R.id.saveUsernameBtn).setOnClickListener {
            if (studentId.isNotEmpty()) {
                db.collection("leaders").document(studentId)
                    .set(
                        hashMapOf("username" to usernameEt.text.toString()),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        Toast.makeText(this, "Username saved", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // Logout
        findViewById<Button>(R.id.logOutBtn).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(applicationContext, LoginActivity::class.java))
            finish()
        }
    }

    // --- Scanner ---

    private fun setupScanner() {
        findViewById<Button>(R.id.scanQrBtn).setOnClickListener {
            if (!shouldBlockTaps()) launchScanner()
        }
    }

    private fun launchScanner() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { raw ->
                    val eventId = extractEventId(raw)
                    attendEventViaApi(eventId)
                }
            }
            .addOnCanceledListener {
                animateToMode(ViewMode.LIST)
            }
            .addOnFailureListener {
                animateToMode(ViewMode.LIST)
            }
    }

    private fun extractEventId(raw: String): String {
        val idx = raw.indexOf("/event/")
        if (idx >= 0) {
            val after = raw.substring(idx + 7)
            val q = after.indexOf('?')
            return if (q >= 0) after.substring(0, q) else after
        }
        return raw
    }

    // ========================================================================
    // API
    // ========================================================================

    private fun attendEventViaApi(eventId: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        user.getIdToken(false).addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener

            Thread {
                try {
                    // Step 1: Get one-time code
                    val codeJson = JSONObject().apply { put("eventId", eventId) }
                    val codeConn = (URL("https://pass.contact/api/code").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Authorization", "Bearer $idToken")
                        doOutput = true
                    }
                    OutputStreamWriter(codeConn.outputStream).use { it.write(codeJson.toString()) }
                    val codeData = codeConn.inputStream.bufferedReader().readText()
                    codeConn.disconnect()
                    val codeRes = JSONObject(codeData)

                    if (codeRes.optString("message") == "already attended.") {
                        runOnUiThread {
                            Toast.makeText(this, "already attended.", Toast.LENGTH_LONG).show()
                        }
                        return@Thread
                    }
                    val code = codeRes.optString("code")
                    if (code.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this, codeRes.optString("message", "No code"), Toast.LENGTH_SHORT).show()
                        }
                        return@Thread
                    }

                    // Step 2: Attend with code
                    val attendJson = JSONObject().apply {
                        put("eventId", eventId)
                        put("code", code)
                        put("fullName", user.displayName ?: "")
                        put("address", "")
                    }
                    val attendConn = (URL("https://pass.contact/api/attend").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Authorization", "Bearer $idToken")
                        doOutput = true
                    }
                    OutputStreamWriter(attendConn.outputStream).use { it.write(attendJson.toString()) }
                    val attendData = attendConn.inputStream.bufferedReader().readText()
                    val attendCode = attendConn.responseCode
                    attendConn.disconnect()

                    runOnUiThread {
                        if (attendCode in 200..299) {
                            val resJson = JSONObject(attendData)
                            val message = resJson.optString("message")
                            val title = resJson.optString("title")

                            if (message == "attended" || message == "already attended.") {
                                Toast.makeText(this, "$message $title", Toast.LENGTH_LONG).show()
                                val intent = Intent(applicationContext, ConfirmationActivity::class.java)
                                intent.putExtra("title", title)
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Server Error", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }

    // ========================================================================
    // Menu (Account Settings)
    // ========================================================================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_account_settings -> {
                openAccountSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openAccountSettings() {
        val url = "https://pass.contact/account"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}
