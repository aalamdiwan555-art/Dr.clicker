package com.dr.clicker.atikapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.regex.Pattern
import kotlin.random.Random

/**
 * Dr.Clicker Accessibility Engine
 * Based on Me Clicker v24.1 core logic
 * Auto-accepts ALL ride requests on Rapido & Ola
 * 100% free, no Firebase, no internet, no login
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DrClicker"
        private const val CHANNEL_ID = "drclicker_service"
        private const val NOTIF_ID = 1
        private const val THROTTLE_MS = 50L
        private const val WAKELOCK_MS = 600000L

        /**
         * Accept-button keywords across Indian languages.
         * Exact match from Me Clicker v24.1 decompiled source.
         */
        val ACCEPT_KEYWORDS = listOf(
            "accept", "confirm", "match", "take ride", "start ride",
            "ஏற்க",
            "స్వీకరించు",
            "ಸ್ವೀಕರಿಸಿ",
            "स्वीकार करें",
            "स्वीकार",
            "स्वीकार करा",
            "গ্রহণ করুন"
        )

        private val NUMBER_RE = Pattern.compile("^[0-9]+\\.?[0-9]*$")
        private val STRIP_NON_NUM = Pattern.compile("[^0-9.]")

        @Volatile
        var isEngineOn = false
            private set
    }

    // Prefs — Me Clicker reads both FlutterSharedPreferences AND MeClickerPrefs
    private lateinit var flutterPrefs: SharedPreferences
    private lateinit var meClickerPrefs: SharedPreferences

    private var logFile: File? = null
    private var isForegroundRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastEventMs = 0L

    private val seenNodes = LinkedHashSet<String>()
    private val handler = Handler(Looper.getMainLooper())

    // ────────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 0L
        }
        setServiceInfo(info)

        flutterPrefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        meClickerPrefs = getSharedPreferences("MeClickerPrefs", MODE_PRIVATE)
        logFile = File(getExternalFilesDir(null), "god_mode_logs.txt")

        acquireWakeLock()
        log("🚀 Dr.Clicker v24.1 PURE CLONE ONLINE | 600000L Wakelock | Exact Click Engine")
    }

    override fun onInterrupt() {
        isEngineOn = false
    }

    override fun onDestroy() {
        isEngineOn = false
        releaseWakeLock()
        super.onDestroy()
    }

    // ────────────────────────────────────────────────────────────────
    // MASTER SWITCH (reads both pref stores like Me Clicker)
    // ────────────────────────────────────────────────────────────────

    private fun getBoolPref(key: String): Boolean {
        // Me Clicker checks FlutterSharedPreferences first (with "flutter." prefix stripped)
        val flutterKey = key.replace("flutter.", "")
        if (flutterPrefs.contains(flutterKey)) {
            return flutterPrefs.getBoolean(flutterKey, false)
        }
        if (meClickerPrefs.contains(key)) {
            return meClickerPrefs.getBoolean(key, false)
        }
        return false
    }

    private fun getIntPref(key: String): Int {
        val flutterKey = key.replace("flutter.", "")
        if (flutterPrefs.contains(flutterKey)) {
            return flutterPrefs.getInt(flutterKey, 0)
        }
        if (meClickerPrefs.contains(key)) {
            return try {
                meClickerPrefs.getInt(key, 0)
            } catch (_: Exception) {
                meClickerPrefs.getLong(key, 0L).toInt()
            }
        }
        return 0
    }

    private fun getFloatPref(key: String): Float {
        val flutterKey = key.replace("flutter.", "")
        if (flutterPrefs.contains(flutterKey)) {
            return flutterPrefs.getFloat(flutterKey, 0f)
        }
        return 0f
    }

    // ────────────────────────────────────────────────────────────────
    // EVENT LOOP
    // ────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val masterOn = getBoolPref("master_switch") || getBoolPref("flutter.masterSwitch")
        isEngineOn = masterOn

        // Foreground service management
        if (masterOn && !isForegroundRunning) {
            startForegroundService()
            isForegroundRunning = true
        } else if (!masterOn && isForegroundRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundRunning = false
            return
        }
        if (!masterOn) return

        // 50ms throttle (exact from Me Clicker)
        val now = System.currentTimeMillis()
        if (now - lastEventMs < THROTTLE_MS) return
        lastEventMs = now

        val allWindows = try { windows } catch (_: Exception) { null } ?: return

        for (win in allWindows) {
            val root = win.root ?: continue
            val type = win.type

            // Skip keyboard windows
            if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                recycleNode(root)
                continue
            }

            val pkg = try {
                root.packageName?.toString()?.lowercase(Locale.ROOT) ?: ""
            } catch (_: Exception) { "" }

            val isRapido = pkg == "com.rapido.rider" || pkg.contains("rapido") || pkg.contains("captain")
            val isOla = pkg == "com.olacabs.oladriver" || (pkg.contains("ola") && pkg.contains("driver"))
            val isPopup = type == AccessibilityWindowInfo.TYPE_SYSTEM

            if (isRapido || isOla || isPopup) {
                val tag = when {
                    isOla -> if (isPopup) "[Ola-Popup]" else "[Ola-InApp]"
                    else -> if (isPopup) "[Rapido-Popup]" else "[Rapido-InApp]"
                }
                scanAndClick(root, now, tag)
            }
            recycleNode(root)
        }
    }

    // ────────────────────────────────────────────────────────────────
    // SCANNER — Accepts ALL rides (no filter checks)
    // ────────────────────────────────────────────────────────────────

    private fun scanAndClick(root: AccessibilityNodeInfo, ts: Long, tag: String) {
        // Collect ALL visible numbers (for logging only)
        val numbers = mutableListOf<Double>()
        collectText(root) { txt ->
            val n = toNumber(txt)
            if (n > 0) numbers.add(n)
        }

        // Extract price/pickup/drop from numbers for logging
        var price = numbers.find { it in 10.0..9999.0 } ?: -1.0
        var pickup = numbers.find { it in 0.1..50.0 } ?: -1.0
        var drop = numbers.find { it in 0.1..200.0 && it != price } ?: -1.0

        // ACCEPT ALL RIDES — no filter checks
        // Just find the accept button and click it
        clickAccept(root, ts, tag, price, pickup, drop)
    }

    // ────────────────────────────────────────────────────────────────
    // CLICKER — Tree walk for accept button
    // ────────────────────────────────────────────────────────────────

    private fun clickAccept(
        node: AccessibilityNodeInfo,
        ts: Long,
        tag: String,
        price: Double,
        pickup: Double,
        drop: Double
    ) {
        val text = try { node.text?.toString()?.trim() ?: "" } catch (_: Exception) { "" }
        val desc = try { node.contentDescription?.toString()?.trim() ?: "" } catch (_: Exception) { "" }
        val combined = "$text $desc".lowercase(Locale.ROOT)

        // Check if this node contains any accept keyword
        if (ACCEPT_KEYWORDS.any { keyword ->
            combined.contains(keyword.lowercase(Locale.ROOT))
        }) {
            val key = "${node.hashCode()}_$ts"
            if (key in seenNodes) return
            seenNodes.add(key)
            // Trim old entries to prevent memory leak
            if (seenNodes.size > 300) {
                val iter = seenNodes.iterator()
                repeat(50) { if (iter.hasNext()) { iter.next(); iter.remove() } }
            }

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val delay = Random.nextLong(10L, 101L)
            val priceStr = if (price > 0) "₹${price.toInt()}" else "₹?"
            val pickupStr = if (pickup > 0) "${pickup}km pickup" else ""
            val dropStr = if (drop > 0) "${drop}km drop" else ""

            val startMs = System.currentTimeMillis()

            if (bounds.width() > 0 && bounds.height() > 0) {
                val cx = bounds.centerX().toFloat()
                val cy = bounds.centerY().toFloat()
                handler.postDelayed({
                    tap(cx, cy)
                    val latency = System.currentTimeMillis() - startMs
                    onAccepted(
                        "$tag ✅ TAP (${cx.toInt()},${cy.toInt()}) $priceStr $pickupStr $dropStr delay=${delay}ms",
                        latency
                    )
                }, delay)
            } else {
                handler.postDelayed({
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    val latency = System.currentTimeMillis() - startMs
                    onAccepted(
                        "$tag ✅ A11Y-CLICK $priceStr $pickupStr $dropStr delay=${delay}ms",
                        latency
                    )
                }, delay)
            }
            return
        }

        // Recurse into children
        val childCount = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            clickAccept(child, ts, tag, price, pickup, drop)
            recycleNode(child)
        }
    }

    private fun onAccepted(msg: String, latencyMs: Long) {
        val count = meClickerPrefs.getInt("accept_count", 0) + 1
        meClickerPrefs.edit()
            .putInt("accept_count", count)
            .putLong("last_latency_ms", latencyMs)
            .apply()
        log("$msg | total=$count | latency=${latencyMs}ms")
        beep()
    }

    // ────────────────────────────────────────────────────────────────
    // GESTURE TAP
    // ────────────────────────────────────────────────────────────────

    private fun tap(x: Float, y: Float) {
        val duration = ViewConfiguration.getTapTimeout().toLong().coerceAtLeast(40L)
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y + 1f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCancelled(gesture: GestureDescription) {
                Log.w(TAG, "Gesture cancelled at ($x,$y)")
            }
        }, handler)
    }

    // ────────────────────────────────────────────────────────────────
    // FOREGROUND NOTIFICATION
    // ────────────────────────────────────────────────────────────────

    private fun startForegroundService() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dr.Clicker Engine", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }

        val pi = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ Dr.Clicker v24.1 PURE CLONE")
            .setContentText("100% Wakelock Engine Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    // ────────────────────────────────────────────────────────────────
    // WAKELOCK
    // ────────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DrClicker:WakeLock")
            wakeLock?.acquire(WAKELOCK_MS)
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    // ────────────────────────────────────────────────────────────────
    // HELPERS
    // ────────────────────────────────────────────────────────────────

    private fun collectText(node: AccessibilityNodeInfo, fn: (String) -> Unit) {
        try {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(fn)
        } catch (_: Exception) {}
        val n = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until n) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            collectText(child, fn)
            recycleNode(child)
        }
    }

    private fun toNumber(text: String): Double {
        return try {
            val clean = STRIP_NON_NUM.matcher(text).replaceAll("")
            if (clean.isNotEmpty() && NUMBER_RE.matcher(clean).matches()) {
                clean.toDouble()
            } else {
                -1.0
            }
        } catch (_: Exception) {
            -1.0
        }
    }

    private fun recycleNode(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < 33) {
            try { node.recycle() } catch (_: Exception) {}
        }
    }

    private fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
            handler.postDelayed({
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                handler.postDelayed({ tg.release() }, 200L)
            }, 150L)
        } catch (_: Exception) {}
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        try {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            FileWriter(logFile, true).use { it.write("[$ts] $msg\n") }
        } catch (_: Exception) {}
    }
}
