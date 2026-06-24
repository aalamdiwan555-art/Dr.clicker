package com.dr.clicker.atikapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
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

class AutoClickService : AccessibilityService() {

    companion object {
        private const val TAG           = "DrClicker"
        private const val CHANNEL_ID    = "drclicker_engine"
        private const val NOTIF_ID      = 42
        private const val THROTTLE_MS   = 50L

        @Volatile var isEngineOn = false
            private set

        /** All accept-button keywords across Indian languages */
        val ACCEPT_KEYWORDS = listOf(
            // English
            "accept", "confirm", "match", "take ride", "start ride",
            // Tamil
            "ஏற்க",
            // Telugu
            "స్వీకరించు",
            // Kannada
            "ಸ್ವೀಕರಿಸಿ",
            // Hindi
            "स्वीकार करें", "स्वीकार",
            // Marathi
            "स्वीकार करा",
            // Bengali
            "গ্রহণ করুন"
        )

        private val NUMBER_RE    = Pattern.compile("^\\d+(\\.\\d+)?$")
        private val STRIP_NON_NUM = Pattern.compile("[^0-9.]")
    }

    // Prefs
    private lateinit var uiPrefs: SharedPreferences   // Flutter writes here
    private lateinit var svcPrefs: SharedPreferences  // service stats

    private var logFile: File? = null
    private var isForeground = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastEventMs = 0L

    private val seen = LinkedHashSet<String>()
    private val handler = Handler(Looper.getMainLooper())

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes          = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType        = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags               = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                                  AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                                  AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 0L
        }
        uiPrefs  = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        svcPrefs = getSharedPreferences("DrClickerPrefs", MODE_PRIVATE)
        logFile  = File(getExternalFilesDir(null), "drclicker_log.txt")
        acquireWakeLock()
        log("⚡ Dr.Clicker v1.0 CONNECTED | WakeLock ON")
    }

    override fun onInterrupt() { isEngineOn = false }

    override fun onDestroy() {
        isEngineOn = false
        releaseWakeLock()
        super.onDestroy()
    }

    // ─── Event Loop ───────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val masterOn = uiPrefs.getBoolean("masterSwitch", false)
        isEngineOn = masterOn

        // Manage foreground service
        if (masterOn && !isForeground) {
            showForegroundNotification(); isForeground = true
        } else if (!masterOn && isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE); isForeground = false; return
        }
        if (!masterOn) return

        // Rate-limit
        val now = System.currentTimeMillis()
        if (now - lastEventMs < THROTTLE_MS) return
        lastEventMs = now

        val allWindows = try { windows } catch (_: Exception) { null } ?: return

        for (win in allWindows) {
            val root = win.root ?: continue
            val type = win.type

            if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                recycle(root); continue
            }

            val pkg = try { root.packageName?.toString()?.lowercase(Locale.ROOT) ?: "" }
                      catch (_: Exception) { "" }

            val rapido = pkg == "com.rapido.rider" || "rapido" in pkg || "captain" in pkg
            val ola    = pkg == "com.olacabs.oladriver" || ("ola" in pkg && "driver" in pkg)
            val popup  = type == AccessibilityWindowInfo.TYPE_SYSTEM

            if (rapido || ola || popup) {
                val tag = when {
                    ola    -> if (popup) "[Ola·Pop]"    else "[Ola·App]"
                    else   -> if (popup) "[Rapido·Pop]" else "[Rapido·App]"
                }
                scan(root, now, tag)
            }
            recycle(root)
        }
    }

    // ─── Scanner ──────────────────────────────────────────────────────────────

    private fun scan(root: AccessibilityNodeInfo, ts: Long, tag: String) {
        // Read saved filters (safe defaults)
        val minPrice  = uiPrefs.getInt("min_price", 0)
        val maxPrice  = uiPrefs.getInt("max_price", 0).let { if (it == 0) 99999 else it }
        val minPickup = uiPrefs.getFloat("min_pickup", 0f)
        val maxDrop   = uiPrefs.getFloat("max_drop", 0f).let { if (it == 0f) 999f else it }

        // Collect visible numbers
        var price  = -1.0
        var pickup = -1.0
        var drop   = -1.0
        collectText(root) { txt ->
            val n = toNumber(txt)
            if (n > 0) when {
                price  < 0 && n in 10.0..9999.0  -> price  = n
                pickup < 0 && n in 0.1..50.0      -> pickup = n
                drop   < 0 && n in 0.1..200.0     -> drop   = n
            }
        }

        // Filter checks
        if (price >= 0 && (price < minPrice || price > maxPrice)) {
            log("$tag ✗ Price ₹$price not in [$minPrice–$maxPrice]"); return
        }
        if (pickup >= 0 && pickup < minPickup) {
            log("$tag ✗ Pickup ${pickup}km < min ${minPickup}km"); return
        }
        if (drop >= 0 && drop > maxDrop) {
            log("$tag ✗ Drop ${drop}km > max ${maxDrop}km"); return
        }

        clickAccept(root, ts, tag, price)
    }

    // ─── Tree Walker ──────────────────────────────────────────────────────────

    private fun clickAccept(
        node: AccessibilityNodeInfo, ts: Long, tag: String, price: Double
    ) {
        val text = try { node.text?.toString()?.trim() ?: "" } catch (_: Exception) { "" }
        val desc = try { node.contentDescription?.toString()?.trim() ?: "" } catch (_: Exception) { "" }
        val combined = "$text $desc".lowercase(Locale.ROOT)

        if (ACCEPT_KEYWORDS.any { combined.contains(it.lowercase(Locale.ROOT)) }) {
            val key = "${node.hashCode()}_$ts"
            if (key in seen) return
            seen.add(key)
            if (seen.size > 300) repeat(50) { seen.iterator().also { it.next(); it.remove() } }

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val delay = Random.nextLong(10L, 101L)
            val priceStr = if (price > 0) "₹${"%.0f".format(price)}" else "₹?"

            val startMs = System.currentTimeMillis()

            if (bounds.width() > 0 && bounds.height() > 0) {
                val cx = bounds.centerX().toFloat()
                val cy = bounds.centerY().toFloat()
                handler.postDelayed({
                    tap(cx, cy)
                    val latency = System.currentTimeMillis() - startMs
                    onAccepted("$tag ✅ TAP (${"%.0f".format(cx)},${cy.toInt()}) $priceStr  ${delay}ms", latency)
                }, delay)
            } else {
                handler.postDelayed({
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    val latency = System.currentTimeMillis() - startMs
                    onAccepted("$tag ✅ A11Y-CLICK $priceStr  ${delay}ms", latency)
                }, delay)
            }
            return
        }

        val n = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until n) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            clickAccept(child, ts, tag, price)
            recycle(child)
        }
    }

    private fun onAccepted(msg: String, latencyMs: Long) {
        val count = svcPrefs.getInt("accept_count", 0) + 1
        svcPrefs.edit()
            .putInt("accept_count", count)
            .putLong("last_latency_ms", latencyMs)
            .apply()
        log("$msg | total=$count | latency=${latencyMs}ms")
        beep()
    }

    // ─── Tap Gesture ─────────────────────────────────────────────────────────

    private fun tap(x: Float, y: Float) {
        val dur = ViewConfiguration.getTapTimeout().toLong().coerceAtLeast(40L)
        val path = Path().apply { moveTo(x, y); lineTo(x, y + 1f) }
        val stroke  = GestureDescription.StrokeDescription(path, 0L, dur)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCancelled(g: GestureDescription) {
                Log.w(TAG, "Gesture cancelled at ($x,$y)")
            }
        }, handler)
    }

    // ─── Foreground Notification ──────────────────────────────────────────────

    private fun showForegroundNotification() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dr.Clicker Engine", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ Dr.Clicker Active")
            .setContentText("Auto-accept engine is running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIF_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(NOTIF_ID, notif)
    }

    // ─── WakeLock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DrClicker:WakeLock")
            wakeLock?.acquire(600_000L)
        } catch (e: Exception) { Log.e(TAG, "WakeLock: ${e.message}") }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun collectText(node: AccessibilityNodeInfo, fn: (String) -> Unit) {
        try { node.text?.toString()?.takeIf { it.isNotBlank() }?.let(fn) } catch (_: Exception) {}
        val n = try { node.childCount } catch (_: Exception) { 0 }
        for (i in 0 until n) {
            val c = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            collectText(c, fn)
            recycle(c)
        }
    }

    private fun toNumber(text: String): Double = try {
        val clean = STRIP_NON_NUM.matcher(text).replaceAll("")
        if (clean.isNotEmpty() && NUMBER_RE.matcher(clean).matches()) clean.toDouble() else -1.0
    } catch (_: Exception) { -1.0 }

    private fun recycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < 33) try { node.recycle() } catch (_: Exception) {}
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
