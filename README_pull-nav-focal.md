# Pull Navigation - Focal Architecture Pattern (Android)

## Overview

This document describes the Android implementation of a gesture-based navigation system using a **focal point architecture** where one central hub (Events list) sits alongside multiple panels that can be revealed through swipe and pull gestures. All panels live within a single `MainActivity` using `FrameLayout` with `translationX`/`translationY` positioning.

## Focal Point Architecture

```
Profile  <--swipe-->  Events (hub)  <--swipe-->  Leaderboard
                          |
                     pull up/down
                          |
                       Scanner
```

### Key Concept: Focal vs Traditional

**Traditional Android Navigation:**
```
Activity A  →  Activity B  →  Activity C
(Separate activities, Intent-based, all equal)
```

**Focal Navigation:**
```
Profile (left)  ←  Events (hub)  →  Leaderboard (right)
                       ↕
                    Scanner (bottom)
```

The **Events list is the hub** - it's always the primary interface that users return to. Other panels are **supporting contexts** revealed through gestures or tapped navigation buttons.

## Architecture

### View Hierarchy

```
MainActivity
└── FrameLayout (focalContainer)
    ├── PANEL: profileView (LinearLayout, off-screen left)
    │   ├── Title "Profile"
    │   ├── Student ID (display)
    │   ├── Username + Save
    │   └── Logout
    │
    ├── PANEL: eventsView (LinearLayout, center - hub)
    │   ├── Header (profile icon | "Events" | leaderboard icon)
    │   ├── ListView (eventlist)
    │   └── Pull hint "Pull up to scan"
    │
    ├── PANEL: leaderboardView (LinearLayout, off-screen right)
    │   ├── Title "Leaderboard"
    │   └── ListView (leaderList)
    │
    ├── PANEL: scannerView (FrameLayout, off-screen bottom)
    │   ├── Header (back icon | "Scan QR Code")
    │   └── Scan QR button
    │
    └── CHROME: floatingNavBtn (TextView, always on top)
        └── "scan" / "back" (adapts to current mode)
```

### State Management

```kotlin
enum class ViewMode { LIST, PROFILE, LEADERBOARD, SCANNER }
enum class DragDirection { NONE, HORIZONTAL, VERTICAL }

private var currentMode = ViewMode.LIST
private var dragDirection = DragDirection.NONE
private var horizontalOffset = 0f
private var verticalOffset = 0f

// Tap-through prevention
private var isDragging = false
private var lastGestureEndTime = 0L
```

## Gesture System

### 1. Directional Gestures

Each panel has specific reveal gestures:

| Panel         | Reveal Gesture         | translationX/Y When Visible | translationX/Y When Hidden |
| ------------- | ---------------------- | --------------------------- | -------------------------- |
| Profile       | Swipe Right from hub   | x: 0                        | x: -screenWidth            |
| Events (hub)  | (Focal)                | x/y: 0                      | (moves opposite of active) |
| Leaderboard   | Swipe Left from hub    | x: 0                        | x: +screenWidth            |
| Scanner       | Pull Up from hub       | y: 0                        | y: +screenHeight           |

### 2. Jitter-Resistant Latch Logic

**Problem:** Touchscreen jitter can cause accidental navigation when scrolling a ListView.

**Solution:** Use Android's `ViewConfiguration.scaledTouchSlop` as the threshold before latching direction:

```kotlin
private var gestureStarted = false
private var isDragIntercepted = false
private var touchSlop = ViewConfiguration.get(this).scaledTouchSlop

override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    when (ev.action) {
        MotionEvent.ACTION_MOVE -> {
            if (!isDragIntercepted) {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY

                // LATCH LOGIC: Wait for meaningful movement before locking direction
                if (!gestureStarted && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    gestureStarted = true

                    // CRITICAL: Check DOMINANT direction to avoid jitter
                    if (abs(dx) > abs(dy)) {
                        dragDirection = DragDirection.HORIZONTAL
                        isDragIntercepted = when (currentMode) {
                            ViewMode.LIST -> true
                            ViewMode.PROFILE -> dx < 0  // Only swipe left to go back
                            ViewMode.LEADERBOARD -> dx > 0  // Only swipe right to go back
                            ViewMode.SCANNER -> false  // No horizontal from scanner
                        }
                    } else {
                        dragDirection = DragDirection.VERTICAL
                        isDragIntercepted = when (currentMode) {
                            ViewMode.LIST -> isAtBottom && dy < 0  // Pull up at bottom
                            ViewMode.SCANNER -> dy > 0  // Pull down to go back
                            else -> false
                        }
                    }
                }
            }
        }
    }
}
```

**Why `abs()` is critical:**
- Real devices produce jitter: small opposite-direction noise
- Example: User swipes down, but touch reports: +5px, +8px, -1px, +12px
- Without `abs()`, that -1px could incorrectly latch as "swiping up"
- With `abs()`, we check MAGNITUDE: |+5| > threshold? |+8| > threshold? etc.

**Why `scaledTouchSlop`:**
- Android's built-in per-device threshold (typically ~8dp)
- Already accounts for screen density and device sensitivity
- More robust than a hardcoded 5px value

### 3. Offset Calculation System

```kotlin
/**
 * Translates all 4 panels based on currentMode + drag offsets.
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
```

### 4. Interactive Transitions

All transitions are **interactive** - the user's finger controls `translationX`/`translationY` in real-time:

```kotlin
if (isDragIntercepted) {
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
    return true  // Consume the event — no animation, just direct offset
}
```

**Why return true without animation?**
- `translationX`/`translationY` updates are immediate (no implicit animation in Android Views)
- User's finger directly controls position = feels responsive
- Only animate on `ACTION_UP` via `ValueAnimator`

### 5. Navigation Triggering

```kotlin
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
        animateToMode(newMode)  // Spring-back or complete navigation
        return true
    }
}
```

### 6. Animation (Spring-Back / Complete)

```kotlin
private fun animateToMode(newMode: ViewMode) {
    data class Pos(val tx: Float, val ty: Float)

    // Capture current pixel positions
    val starts = mapOf(
        profilePanel to Pos(profilePanel.translationX, profilePanel.translationY),
        eventsPanel to Pos(eventsPanel.translationX, eventsPanel.translationY),
        leaderboardPanel to Pos(leaderboardPanel.translationX, leaderboardPanel.translationY),
        scannerPanel to Pos(scannerPanel.translationX, scannerPanel.translationY)
    )

    // Calculate end positions for new mode
    currentMode = newMode
    horizontalOffset = 0f
    verticalOffset = 0f
    updateViewTranslations()

    val ends = mapOf(/* ... capture end positions ... */)

    // Animate from current → target (300ms decelerate)
    isAnimating = true
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 300
        interpolator = DecelerateInterpolator(2f)
        addUpdateListener { anim ->
            val p = anim.animatedValue as Float
            for (view in panels) {
                view.translationX = starts[view]!!.tx + (ends[view]!!.tx - starts[view]!!.tx) * p
                view.translationY = starts[view]!!.ty + (ends[view]!!.ty - starts[view]!!.ty) * p
            }
        }
        addListener(onEnd = { isAnimating = false })
        start()
    }
}
```

**Why capture start/end positions?**
- Changing `currentMode` shifts the base offsets immediately
- If we animated `horizontalOffset` to 0 after changing mode, there'd be a visual jump
- By capturing pixel positions before and after, we interpolate smoothly with no discontinuity

## Preventing Tap-Through

### The Problem

During swipe navigation, buttons on the destination panel can receive tap events that were queued during the gesture and fire after navigation completes.

### The Solution: `shouldBlockTaps()` Guard

**1. Guard all click listeners:**

```kotlin
// All interactive elements check shouldBlockTaps() before acting
findViewById<ImageButton>(R.id.profileNavBtn).setOnClickListener {
    if (!shouldBlockTaps()) animateToMode(ViewMode.PROFILE)
}

eventList.onItemClickListener = OnItemClickListener { _, _, position, _ ->
    if (shouldBlockTaps()) return@OnItemClickListener
    // ... handle click
}
```

**2. Gesture state tracking:**

```kotlin
// Tap-through prevention (GestureStateManager equivalent)
private var isDragging = false
private var lastGestureEndTime = 0L

private fun endDrag(didNavigate: Boolean) {
    isDragging = false
    lastGestureEndTime = System.currentTimeMillis()
}

private fun shouldBlockTaps(): Boolean {
    if (isDragging) return true
    val elapsed = System.currentTimeMillis() - lastGestureEndTime
    return elapsed < 400  // Block for 400ms after any gesture
}
```

**Why this is simpler than iOS:**
- Android View click listeners run synchronously on the main thread
- No need for `@Published` vs private property distinction (no Combine)
- No need for `DispatchWorkItem` cleanup — simple timestamp comparison
- Single Activity owns all state — no environment injection needed

## ListView Touch Conflict Resolution

### The Problem

`dispatchTouchEvent` runs before child views receive events. Intercepting horizontal swipes must not break vertical ListView scrolling.

### The Solution: Cancel Dispatch

```kotlin
if (isDragIntercepted) {
    // Send ACTION_CANCEL to children so ListView stops scrolling
    if (!cancelSentToChildren) {
        cancelSentToChildren = true
        val cancel = MotionEvent.obtain(ev)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    // Handle gesture ourselves
    updateViewTranslations()
    return true
}
```

**How it works:**
1. `ACTION_DOWN` always passes to children (ListView starts tracking)
2. On `ACTION_MOVE`, if we detect a navigation gesture:
   - Send `ACTION_CANCEL` to children (ListView abandons its scroll)
   - Consume all subsequent events ourselves
3. If the gesture is NOT a navigation gesture (e.g. vertical scroll in list):
   - We never set `isDragIntercepted = true`
   - All events pass through to ListView normally

### Scroll Position Tracking

```kotlin
// Track when user has scrolled to bottom of events list
eventList.setOnScrollListener(object : AbsListView.OnScrollListener {
    override fun onScroll(
        view: AbsListView, firstVisible: Int, visibleCount: Int, totalCount: Int
    ) {
        isAtBottom = totalCount > 0 && (firstVisible + visibleCount >= totalCount)
    }
    override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}
})
```

**Android advantage over iOS:**
- `AbsListView.OnScrollListener` is a direct callback — no coordinate-space geometry needed
- No SwiftUI `GeometryReader` / `ScrollPositionTracker` complexity
- No "both conditions true" bug — only one `isAtBottom` boolean, updated by one ListView
- No four-layer defense needed (that was a SwiftUI-specific issue with `switch` materializing all cases)

## Floating Navigation Button

Matches iOS `backButton` behavior:

```kotlin
private fun updateFloatingNav() {
    when (currentMode) {
        ViewMode.SCANNER -> floatingNavBtn.visibility = View.GONE
        ViewMode.LIST -> {
            floatingNavBtn.visibility = View.VISIBLE
            floatingNavBtn.text = "scan"        // Tapping goes to scanner
        }
        else -> {
            floatingNavBtn.visibility = View.VISIBLE
            floatingNavBtn.text = "back"        // Tapping goes to hub
        }
    }
}
```

The floating button uses `android:elevation="8dp"` to stay above all panels regardless of their `translationZ`.

## Back Button Handling

Android system back button returns to the hub:

```kotlin
onBackPressedDispatcher.addCallback(this,
    object : OnBackPressedCallback(true) {
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
```

## Key Differences: Android vs iOS Implementation

| Aspect                | iOS (SwiftUI)                        | Android (Views)                          |
| --------------------- | ------------------------------------ | ---------------------------------------- |
| **Layout Container**  | `ZStack`                             | `FrameLayout`                            |
| **Positioning**       | `.offset(x:y:)`                      | `view.translationX` / `translationY`     |
| **Gesture Capture**   | `.gesture(DragGesture())`            | `dispatchTouchEvent()` override          |
| **Touch Slop**        | Hardcoded 5px                        | `ViewConfiguration.scaledTouchSlop`      |
| **Animation**         | `withAnimation(.spring())`           | `ValueAnimator` + `DecelerateInterpolator` |
| **No-Animation Drag** | `.animation(nil, value:)`            | Implicit (Views don't auto-animate)      |
| **Tap Blocking**      | `QuietButton` + `GestureStateManager` + `@EnvironmentObject` | `shouldBlockTaps()` guard in click listeners |
| **Scroll Tracking**   | `GeometryReader` + `ScrollPositionTracker` + 4-layer defense | `AbsListView.OnScrollListener` (one line) |
| **Child Cancel**      | SwiftUI handles internally           | Manual `ACTION_CANCEL` dispatch          |
| **Back Nav**          | Floating "back" text                 | Floating "back" text + system back button |
| **Scanner**           | `CodeScannerView` (embedded camera)  | `GmsBarcodeScanning` (popup overlay)     |
| **State Scope**       | `@State` / `@StateObject`            | Activity instance variables              |

## Testing Checklist

- [ ] Swipe left from Events reveals Leaderboard
- [ ] Swipe right from Events reveals Profile
- [ ] Pull up at bottom of Events reveals Scanner
- [ ] Pull down from Scanner returns to Events
- [ ] Swipe left from Profile returns to Events
- [ ] Swipe right from Leaderboard returns to Events
- [ ] Interactive transitions follow finger position
- [ ] Spring-back animation on incomplete gestures
- [ ] No tap-through on destination panels after navigation
- [ ] ListView scrolls normally when not at bottom
- [ ] Pull-up only works when scrolled to bottom of events list
- [ ] Jitter doesn't trigger wrong navigation direction
- [ ] Android back button returns to Events hub
- [ ] Floating nav shows "scan" on Events, "back" on others, hidden on Scanner
- [ ] Scanner auto-launches when entering Scanner panel

## Performance Notes

- All 4 panels rendered at all times (match_parent in FrameLayout)
- Trade-off: Instant navigation, no activity transitions or loading states
- `translationX`/`translationY` is hardware-accelerated (no layout passes)
- `ValueAnimator` runs on main thread — keep callbacks lightweight
- `isAnimating` flag prevents gesture handling during animation (avoids conflicts)
- ListView adapters set once via Firestore snapshot listeners (not re-created on navigation)
