package com.sayists.passport

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.MaterialColors
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    private lateinit var previewOverlay: View
    private lateinit var previewLogoutBtn: Button
    private var isPreviewMode = false

    private lateinit var usernameDivider: View
    private lateinit var fullNameDivider: View
    private lateinit var addressLine1Divider: View
    private lateinit var addressLine2Divider: View
    private lateinit var cityDivider: View
    private lateinit var stateDivider: View
    private lateinit var zipDivider: View

    private var firestoreUsername = ""
    private var firestoreFullName = ""
    private var firestoreAddressLine1 = ""
    private var firestoreAddressLine2 = ""
    private var firestoreCity = ""
    private var firestoreState = ""
    private var firestoreZip = ""

    // --- Scanner ---
    private lateinit var previewView: PreviewView
    private lateinit var scanHintTv: TextView
    private lateinit var barcodeScanner: BarcodeScanner
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var previewUseCase: Preview? = null
    private lateinit var analysisExecutor: ExecutorService
    private var isScannerActive = false
    private var scanLocked = false
    private val scanHintHandler = Handler(Looper.getMainLooper())
    private var scanHintReset: Runnable? = null
    //private var lastHintAt = 0L
    private val cameraPermissionRequest = 1001

    // --- Data ---
    private val db = FirebaseFirestore.getInstance()
    private val descriptionLinks = ArrayList<String>()
    private val eventIds = ArrayList<String>()
    private val eventTitles = ArrayList<String>()
    private val eventDates = ArrayList<String>()
    private val eventLocations = ArrayList<String>()

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
        previewOverlay = findViewById(R.id.previewOverlay)
        previewLogoutBtn = findViewById(R.id.previewLogoutBtn)
        previewView = findViewById(R.id.scannerPreview)
        scanHintTv = findViewById(R.id.scanHintTv)
        val profileScroll = findViewById<ScrollView>(R.id.profileScroll)

        // Edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.focalContainer)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Add bottom padding when IME is visible so fields stay scrollable
        val baseProfilePadding = profileScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(profileScroll) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, max(baseProfilePadding, imeBottom))
            insets
        }

        // Scanner setup
        analysisExecutor = Executors.newSingleThreadExecutor()
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

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

        isPreviewMode = intent.getBooleanExtra("previewMode", false)
        if (isPreviewMode) {
            previewOverlay.visibility = View.VISIBLE
            previewLogoutBtn.setOnClickListener {
                FirebaseAuth.getInstance().signOut()
                startActivity(Intent(applicationContext, LoginActivity::class.java))
                finish()
            }
        } else {
            previewOverlay.visibility = View.GONE
        }

        // Initial positioning (no animation)
        positionViewsImmediate()
    }

    override fun onPause() {
        super.onPause()
        stopCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        barcodeScanner.close()
        analysisExecutor.shutdown()
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

    private fun positionViewsImmediate() {
        currentMode = ViewMode.LIST
        horizontalOffset = 0f
        verticalOffset = 0f
        updateViewTranslations()
        updateFloatingNav()
    }

    private fun animateToMode(newMode: ViewMode) {
        val previousMode = currentMode
        if (previousMode == ViewMode.SCANNER && newMode != ViewMode.SCANNER) {
            stopCamera()
        }
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
                if (url.isNotEmpty() && isValidHttpsUrl(url)) {
                    // Open URL if set and valid HTTPS
                    val intent = Intent(applicationContext, WebviewActivity::class.java)
                    intent.putExtra("url", url)
                    startActivity(intent)
                } else {
                    // Open event detail page (directions)
                    val intent = Intent(applicationContext, EventDetailActivity::class.java)
                    intent.putExtra("eventId", eventIds[position])
                    intent.putExtra("title", eventTitles[position])
                    intent.putExtra("date", eventDates[position])
                    intent.putExtra("location", eventLocations[position])
                    startActivity(intent)
                }
            }
        }

        // Long press always opens event detail (directions) regardless of descriptionLink
        eventList.onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, position, _ ->
            if (shouldBlockTaps()) return@OnItemLongClickListener true
            if (position < eventIds.size) {
                val intent = Intent(applicationContext, EventDetailActivity::class.java)
                intent.putExtra("eventId", eventIds[position])
                intent.putExtra("title", eventTitles[position])
                intent.putExtra("date", eventDates[position])
                intent.putExtra("location", eventLocations[position])
                startActivity(intent)
            }
            true
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
                eventIds.clear()
                eventTitles.clear()
                eventDates.clear()
                eventLocations.clear()
                val eventDisplayNames = ArrayList<String>()

                for (doc in value!!) {
                    val date = doc.getString("date") ?: ""
                    val title = doc.getString("title") ?: ""
                    val location = doc.getString("location") ?: ""
                    descriptionLinks.add(doc.getString("descriptionLink") ?: "")
                    eventIds.add(doc.id)
                    eventTitles.add(title)
                    eventDates.add(date)
                    eventLocations.add(location)
                    eventDisplayNames.add("$date $title: $location")
                }

                val mListView = findViewById<ListView>(R.id.eventlist)
                mListView.adapter = ArrayAdapter(
                    this, android.R.layout.simple_list_item_1, eventDisplayNames
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
                    //val studentId = doc.id
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
        val fullNameEt = findViewById<EditText>(R.id.fullNameEt)
        val addressLine1Et = findViewById<EditText>(R.id.addressLine1Et)
        val addressLine2Et = findViewById<EditText>(R.id.addressLine2Et)
        val cityEt = findViewById<EditText>(R.id.cityEt)
        val stateEt = findViewById<EditText>(R.id.stateEt)
        val zipCodeEt = findViewById<EditText>(R.id.zipCodeEt)
        usernameDivider = findViewById(R.id.usernameDivider)
        fullNameDivider = findViewById(R.id.fullNameDivider)
        addressLine1Divider = findViewById(R.id.addressLine1Divider)
        addressLine2Divider = findViewById(R.id.addressLine2Divider)
        cityDivider = findViewById(R.id.cityDivider)
        stateDivider = findViewById(R.id.stateDivider)
        zipDivider = findViewById(R.id.zipDivider)

        val prefs = getSharedPreferences("profile_draft", MODE_PRIVATE)
        val outlineColor = MaterialColors.getColor(
            usernameDivider,
            com.google.android.material.R.attr.colorOutline
        )
        val highlightColor = ContextCompat.getColor(this, R.color.mu_blue)

        fun normalize(value: String): String = value.trim()

        fun updateDivider(divider: View, dirty: Boolean) {
            divider.setBackgroundColor(if (dirty) highlightColor else outlineColor)
        }

        fun updateIndicators() {
            updateDivider(usernameDivider, normalize(usernameEt.text.toString()) != normalize(firestoreUsername))
            updateDivider(fullNameDivider, normalize(fullNameEt.text.toString()) != normalize(firestoreFullName))
            updateDivider(addressLine1Divider, normalize(addressLine1Et.text.toString()) != normalize(firestoreAddressLine1))
            updateDivider(addressLine2Divider, normalize(addressLine2Et.text.toString()) != normalize(firestoreAddressLine2))
            updateDivider(cityDivider, normalize(cityEt.text.toString()) != normalize(firestoreCity))
            updateDivider(stateDivider, normalize(stateEt.text.toString()) != normalize(firestoreState))
            updateDivider(zipDivider, normalize(zipCodeEt.text.toString()) != normalize(firestoreZip))
        }

        usernameEt.doAfterTextChanged { updateIndicators() }
        fullNameEt.doAfterTextChanged { updateIndicators() }
        addressLine1Et.doAfterTextChanged { updateIndicators() }
        addressLine2Et.doAfterTextChanged { updateIndicators() }
        cityEt.doAfterTextChanged { updateIndicators() }
        stateEt.doAfterTextChanged { updateIndicators() }
        zipCodeEt.doAfterTextChanged { updateIndicators() }

        // Load profile data from Firestore
        if (studentId.isNotEmpty()) {
            db.collection("leaders").document(studentId).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        firestoreUsername = doc.getString("username") ?: ""
                        firestoreFullName = doc.getString("fullName") ?: ""
                        if (firestoreUsername.isNotEmpty()) {
                            usernameEt.setText(firestoreUsername)
                        } else {
                            usernameEt.setText(prefs.getString("username", "") ?: "")
                        }
                        if (firestoreFullName.isNotEmpty()) {
                            fullNameEt.setText(firestoreFullName)
                        } else {
                            fullNameEt.setText(prefs.getString("fullName", "") ?: "")
                        }

                        // Parse address if it exists
                        val address = (doc.getString("address") ?: "").trim()
                        if (address.isNotEmpty()) {
                            val parts = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            if (parts.size >= 3) {
                                firestoreAddressLine1 = parts[0]
                                val hasLine2 = parts.size > 3
                                if (hasLine2) {
                                    firestoreAddressLine2 = parts[1]
                                    firestoreCity = parts.getOrNull(2) ?: ""
                                    val stateZip = parts.getOrNull(3) ?: ""
                                    val stateZipParts = stateZip.split(" ").filter { it.isNotEmpty() }
                                    firestoreState = stateZipParts.getOrNull(0) ?: ""
                                    firestoreZip = stateZipParts.getOrNull(1) ?: ""
                                } else {
                                    firestoreAddressLine2 = ""
                                    firestoreCity = parts.getOrNull(1) ?: ""
                                    val stateZip = parts.getOrNull(2) ?: ""
                                    val stateZipParts = stateZip.split(" ").filter { it.isNotEmpty() }
                                    firestoreState = stateZipParts.getOrNull(0) ?: ""
                                    firestoreZip = stateZipParts.getOrNull(1) ?: ""
                                }

                                addressLine1Et.setText(firestoreAddressLine1)
                                addressLine2Et.setText(firestoreAddressLine2)
                                cityEt.setText(firestoreCity)
                                stateEt.setText(firestoreState)
                                zipCodeEt.setText(firestoreZip)
                            }
                        } else {
                            firestoreAddressLine1 = ""
                            firestoreAddressLine2 = ""
                            firestoreCity = ""
                            firestoreState = ""
                            firestoreZip = ""
                            addressLine1Et.setText(prefs.getString("addressLine1", "") ?: "")
                            addressLine2Et.setText(prefs.getString("addressLine2", "") ?: "")
                            cityEt.setText(prefs.getString("city", "") ?: "")
                            stateEt.setText(prefs.getString("state", "") ?: "")
                            zipCodeEt.setText(prefs.getString("zipCode", "") ?: "")
                        }
                        updateIndicators()
                    } else {
                        firestoreUsername = ""
                        firestoreFullName = ""
                        firestoreAddressLine1 = ""
                        firestoreAddressLine2 = ""
                        firestoreCity = ""
                        firestoreState = ""
                        firestoreZip = ""
                        usernameEt.setText(prefs.getString("username", "") ?: "")
                        fullNameEt.setText(prefs.getString("fullName", "") ?: "")
                        addressLine1Et.setText(prefs.getString("addressLine1", "") ?: "")
                        addressLine2Et.setText(prefs.getString("addressLine2", "") ?: "")
                        cityEt.setText(prefs.getString("city", "") ?: "")
                        stateEt.setText(prefs.getString("state", "") ?: "")
                        zipCodeEt.setText(prefs.getString("zipCode", "") ?: "")
                        updateIndicators()
                    }
                }
        } else {
            usernameEt.setText(prefs.getString("username", "") ?: "")
            fullNameEt.setText(prefs.getString("fullName", "") ?: "")
            addressLine1Et.setText(prefs.getString("addressLine1", "") ?: "")
            addressLine2Et.setText(prefs.getString("addressLine2", "") ?: "")
            cityEt.setText(prefs.getString("city", "") ?: "")
            stateEt.setText(prefs.getString("state", "") ?: "")
            zipCodeEt.setText(prefs.getString("zipCode", "") ?: "")
            updateIndicators()
        }

        // Save profile
        findViewById<Button>(R.id.saveProfileBtn).setOnClickListener {
            if (studentId.isNotEmpty()) {
                val username = usernameEt.text.toString()
                val fullName = fullNameEt.text.toString()
                val line1 = addressLine1Et.text.toString()
                val line2 = addressLine2Et.text.toString()
                val city = cityEt.text.toString()
                val state = stateEt.text.toString()
                val zip = zipCodeEt.text.toString()

                // Build address string
                val address = if (line1.isNotEmpty() && city.isNotEmpty() && state.isNotEmpty() && zip.isNotEmpty()) {
                    if (line2.isEmpty()) {
                        "$line1, $city, $state $zip"
                    } else {
                        "$line1, $line2, $city, $state $zip"
                    }
                } else {
                    ""
                }

                prefs.edit()
                    .putString("username", username)
                    .putString("fullName", fullName)
                    .putString("addressLine1", line1)
                    .putString("addressLine2", line2)
                    .putString("city", city)
                    .putString("state", state)
                    .putString("zipCode", zip)
                    .apply()

                db.collection("leaders").document(studentId)
                    .set(
                        hashMapOf(
                            "username" to username,
                            "fullName" to fullName,
                            "address" to address
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .addOnSuccessListener {
                        firestoreUsername = username
                        firestoreFullName = fullName
                        firestoreAddressLine1 = line1
                        firestoreAddressLine2 = line2
                        firestoreCity = city
                        firestoreState = state
                        firestoreZip = zip
                        updateIndicators()
                        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Save failed: ${it.message}", Toast.LENGTH_SHORT).show()
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
        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }
        startCamera()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        requestPermissions(arrayOf(Manifest.permission.CAMERA), cameraPermissionRequest)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequest) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
                animateToMode(ViewMode.LIST)
            }
        }
    }

    private fun startCamera() {
        if (isScannerActive) return
        isScannerActive = true
        scanLocked = false
        setScanHint("Point at a Passport event QR code")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                processImage(imageProxy)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            previewUseCase = preview
            analysisUseCase = analysis
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        if (!isScannerActive) return
        isScannerActive = false
        scanLocked = true
        analysisUseCase?.clearAnalyzer()
        cameraProvider?.unbindAll()
        runOnUiThread {
            scanHintReset?.let { scanHintHandler.removeCallbacks(it) }
            scanHintTv.text = "Tap to launch scanner\nScreen scans off"
        }
    }

    private data class FrameLuma(
        val buffer: ByteBuffer,
        val width: Int,
        val height: Int,
        val rowStride: Int,
        val pixelStride: Int
    )

    private fun processImage(imageProxy: ImageProxy) {
        if (!isScannerActive || scanLocked) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                val raw = barcode?.rawValue
                val box = barcode?.boundingBox
                if (raw != null && box != null && !scanLocked) {
                    val yPlane = mediaImage.planes[0]
                    val frame = FrameLuma(
                        yPlane.buffer.duplicate(),
                        mediaImage.width,
                        mediaImage.height,
                        yPlane.rowStride,
                        yPlane.pixelStride
                    )
                    val mappedBox = mapRectToImageSpace(
                        box,
                        imageProxy.imageInfo.rotationDegrees,
                        frame.width,
                        frame.height
                    )
                    val whiteOk = passesWhiteBorderCheck(frame, mappedBox)
                    val textureOk = passesTextureCheck(frame, mappedBox)
                    if (!whiteOk || !textureOk) {
                        if (!whiteOk && !textureOk) {
                            setScanHint("Needs white paper and the official printout.")
                        } else if (!whiteOk) {
                            setScanHint("Place the QR on white paper under bright light.")
                        } else {
                            setScanHint("Printed QR texture missing. Use the official printout.")
                        }
                        return@addOnSuccessListener
                    }
                    scanLocked = true
                    val eventId = extractEventId(raw)
                    stopCamera()
                    animateToMode(ViewMode.LIST)
                    attendEventViaApi(eventId)
                }
            }
            .addOnFailureListener {
                // Keep scanning; no-op
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun mapRectToImageSpace(
        rect: Rect,
        rotationDegrees: Int,
        width: Int,
        height: Int
    ): Rect {
        val points = listOf(
            PointF(rect.left.toFloat(), rect.top.toFloat()),
            PointF(rect.right.toFloat(), rect.top.toFloat()),
            PointF(rect.left.toFloat(), rect.bottom.toFloat()),
            PointF(rect.right.toFloat(), rect.bottom.toFloat())
        )
        val mapped = points.map { p ->
            when (rotationDegrees) {
                90 -> PointF(p.y, height - p.x - 1)
                180 -> PointF(width - p.x - 1, height - p.y - 1)
                270 -> PointF(width - p.y - 1, p.x)
                else -> PointF(p.x, p.y)
            }
        }
        val minX = mapped.minOf { it.x }.toInt()
        val maxX = mapped.maxOf { it.x }.toInt()
        val minY = mapped.minOf { it.y }.toInt()
        val maxY = mapped.maxOf { it.y }.toInt()
        return Rect(
            max(0, minX),
            max(0, minY),
            min(width - 1, maxX),
            min(height - 1, maxY)
        )
    }

    private fun setScanHint(message: String) {
        runOnUiThread {
            scanHintTv.text = "$message\nScreen scans off"
            scanHintReset?.let { scanHintHandler.removeCallbacks(it) }
            val reset = Runnable {
                scanHintTv.text = "Point at a Passport event QR code\nScreen scans off"
            }
            scanHintReset = reset
            scanHintHandler.postDelayed(reset, 2500)
        }
    }

    private fun lumaAt(frame: FrameLuma, x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= frame.width || y >= frame.height) return 0
        val index = y * frame.rowStride + x * frame.pixelStride
        if (index < 0 || index >= frame.buffer.capacity()) return 0
        return frame.buffer.get(index).toInt() and 0xFF
    }

    private fun passesWhiteBorderCheck(frame: FrameLuma, box: Rect): Boolean {
        val minX = max(0, box.left)
        val minY = max(0, box.top)
        val maxX = min(frame.width - 1, box.right)
        val maxY = min(frame.height - 1, box.bottom)
        if (maxX <= minX || maxY <= minY) return false

        val boxW = maxX - minX
        val boxH = maxY - minY
        val margin = max(6, (min(boxW, boxH) * 0.08).toInt())
        val ringMinX = max(0, minX - margin)
        val ringMinY = max(0, minY - margin)
        val ringMaxX = min(frame.width - 1, maxX + margin)
        val ringMaxY = min(frame.height - 1, maxY + margin)

        var whiteCount = 0
        var total = 0
        val step = 2
        var y = ringMinY
        while (y <= ringMaxY) {
            var x = ringMinX
            while (x <= ringMaxX) {
                val inBox = x in minX..maxX && y in minY..maxY
                if (!inBox) {
                    val luma = lumaAt(frame, x, y)
                    total++
                    if (luma >= 230) whiteCount++
                }
                x += step
            }
            y += step
        }
        if (total == 0) return false
        return whiteCount.toDouble() / total >= 0.7
    }

    private fun passesTextureCheck(frame: FrameLuma, box: Rect): Boolean {
        val minX = max(0, box.left)
        val minY = max(0, box.top)
        val maxX = min(frame.width - 1, box.right)
        val maxY = min(frame.height - 1, box.bottom)
        if (maxX <= minX || maxY <= minY) return false

        val boxW = maxX - minX
        val boxH = maxY - minY
        val inset = max(6, (min(boxW, boxH) * 0.08).toInt())
        val startX = minX + inset
        val endX = maxX - inset
        val startY = minY + inset
        val endY = maxY - inset
        if (endX <= startX || endY <= startY) return false

        var count = 0
        var mean = 0.0
        var m2 = 0.0
        var deviation = 0.0
        val step = 2
        var y = startY
        while (y <= endY) {
            var x = startX
            while (x <= endX) {
                val luma = lumaAt(frame, x, y).toDouble()
                if (luma >= 200) {
                    val lumaRight = lumaAt(frame, min(x + 1, frame.width - 1), y).toDouble()
                    val lumaDown = lumaAt(frame, x, min(y + 1, frame.height - 1)).toDouble()
                    val gradient = abs(luma - lumaRight) + abs(luma - lumaDown)
                    if (gradient <= 12) {
                        count++
                        val delta = luma - mean
                        mean += delta / count
                        m2 += delta * (luma - mean)
                        deviation += abs(luma - (lumaRight + lumaDown) / 2.0)
                    }
                }
                x += step
            }
            y += step
        }
        if (count < 160) return false
        val variance = m2 / max(1, count - 1)
        val avgDeviation = deviation / count
        return variance >= 14 && avgDeviation >= 4
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

    private fun isValidEventId(eventId: String): Boolean {
        // Event IDs should be non-empty, reasonable length, and alphanumeric with some special chars
        if (eventId.isEmpty() || eventId.length > 200) return false
        // Allow alphanumeric, hyphens, underscores (common Firestore ID chars)
        return eventId.matches(Regex("^[a-zA-Z0-9_-]+$"))
    }

    private fun isValidHttpsUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            uri.scheme == "https" && !uri.host.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // ========================================================================
    // API
    // ========================================================================

    private fun showErrorDialog(message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ ->
                    animateToMode(ViewMode.LIST)
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun attendEventViaApi(eventId: String) {
        // Validate eventId format
        if (!isValidEventId(eventId)) {
            showErrorDialog("Invalid event ID format: '$eventId'\n\nPlease scan a valid QR code from the Passport system.")
            return
        }

        val user = FirebaseAuth.getInstance().currentUser

        // If not signed in, prompt to sign in
        if (user == null) {
            runOnUiThread {
                Toast.makeText(this, "Please sign in to attend events", Toast.LENGTH_LONG).show()
                startActivity(Intent(applicationContext, LoginActivity::class.java))
            }
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { result ->
                val idToken = result.token
                if (idToken == null) {
                    showErrorDialog("Authentication failed - please sign in again")
                    return@addOnSuccessListener
                }

                Thread {
                    try {
                    // Step 1: Attend directly (no one-time code)
                    // Get saved profile data from Firestore (blocking call in background thread)
                    val studentId = user.email?.substringBefore("@")?.trim() ?: ""
                    var savedFullName = ""
                    var savedAddress = ""

                    if (studentId.isNotEmpty()) {
                        try {
                            val leaderDoc = Tasks.await(db.collection("leaders").document(studentId).get())
                            // Ensure we get strings, not null or other types
                            savedFullName = (leaderDoc.getString("fullName") ?: "").trim()
                            savedAddress = (leaderDoc.getString("address") ?: "").trim()
                        } catch (e: Exception) {
                            // Continue with empty values if fetch fails
                        }
                    }

                    // Validate all fields are properly typed as strings
                    val attendJson = JSONObject().apply {
                        put("eventId", eventId.trim())
                        put("fullName", savedFullName)
                        put("address", savedAddress)
                    }
                    val attendConn = (URL("https://pass.contact/api/attend").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Authorization", "Bearer $idToken")
                        doOutput = true
                        // Set timeouts to prevent indefinite hangs (30 seconds)
                        connectTimeout = 30000
                        readTimeout = 30000
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
                            showErrorDialog("Server returned error code: $attendCode\n\nResponse: $attendData\n\nSent data:\n${attendJson.toString(2)}")
                        }
                    }
                } catch (e: IOException) {
                    showErrorDialog("Network error: ${e.message}\n\nStack trace:\n${e.stackTraceToString().take(500)}")
                }
            }.start()
        }
        .addOnFailureListener { e ->
            showErrorDialog("Failed to get authentication token: ${e.message}")
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
