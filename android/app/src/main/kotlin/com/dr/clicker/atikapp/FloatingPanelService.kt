package com.dr.clicker.atikapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Dr.Clicker Floating Panel
 * Shows a draggable overlay on top of ALL apps (Rapido, Ola, etc.)
 * Displays engine status, ride count, and filter info
 */
class FloatingPanelService : Service() {

    companion object {
        private const val TAG = "DrFloating"
        private const val CHANNEL_ID = "drclicker_floating"
        private const val NOTIF_ID = 2

        @Volatile
        var isRunning = false

        fun canDraw(ctx: android.content.Context): Boolean =
            if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(ctx) else true
    }

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var tvStatus: TextView? = null
    private var tvCount: TextView? = null
    private var tvFilter: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences

    private var initX = 0; private var initY = 0
    private var initTouchX = 0f; private var initTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        prefs = getSharedPreferences("MeClickerPrefs", MODE_PRIVATE)
        startForegroundNotif()
        createFloatingView()
        startUpdater()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        try { windowManager?.removeView(floatView) } catch (_: Exception) {}
        floatView = null
        super.onDestroy()
    }

    private fun startForegroundNotif() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dr.Clicker Panel", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ Dr.Clicker Panel Active")
            .setContentText("Floating panel is running on screen")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun createFloatingView() {
        if (!canDraw(this)) {
            Log.w(TAG, "No overlay permission — panel cannot show")
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val wlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 220
        }

        val panel = buildPanel()
        floatView = panel

        panel.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initX = wlp.x; initY = wlp.y
                        initTouchX = event.rawX; initTouchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        wlp.x = (initX - (event.rawX - initTouchX)).toInt()
                        wlp.y = (initY + (event.rawY - initTouchY)).toInt()
                        windowManager?.updateViewLayout(panel, wlp)
                    }
                }
                return true
            }
        })

        try {
            windowManager?.addView(panel, wlp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add view: ${e.message}")
            stopSelf()
        }
    }

    private fun buildPanel(): View {
        val ctx = this
        val dp = resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10*dp).toInt(), (8*dp).toInt(), (10*dp).toInt(), (8*dp).toInt())
            background = buildBackground(dp)
            elevation = 8f * dp
        }

        val titleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams((9*dp).toInt(), (9*dp).toInt()).also {
                it.marginEnd = (6*dp).toInt()
            }
            val dotBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#00E676"))
            }
            background = dotBg
        }
        titleRow.addView(dot)

        val title = TextView(ctx).apply {
            text = "⚡ Dr.Clicker"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        titleRow.addView(title)
        root.addView(titleRow)

        tvStatus = TextView(ctx).apply {
            text = "Engine: --"
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(0, (3*dp).toInt(), 0, 0)
        }
        root.addView(tvStatus)

        tvCount = TextView(ctx).apply {
            text = "Rides: 0"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, (2*dp).toInt(), 0, 0)
        }
        root.addView(tvCount)

        tvFilter = TextView(ctx).apply {
            text = "Filter: All"
            setTextColor(Color.parseColor("#90A4AE"))
            textSize = 9f
            setPadding(0, (2*dp).toInt(), 0, 0)
        }
        root.addView(tvFilter)

        return root
    }

    private fun buildBackground(dp: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 14f * dp
            setColor(Color.parseColor("#DD111111"))
            setStroke((1.5f * dp).toInt(), Color.parseColor("#66FFD600"))
        }
    }

    private fun startUpdater() {
        handler.post(object : Runnable {
            override fun run() {
                updatePanel()
                handler.postDelayed(this, 1000L)
            }
        })
    }

    private fun updatePanel() {
        val engineOn = MyAccessibilityService.isEngineOn
        val count = prefs.getInt("accept_count", 0)
        val flPrefs = getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)
        val minP = flPrefs.getInt("min_price", 0)
        val maxP = flPrefs.getInt("max_price", 0)

        tvStatus?.text = if (engineOn) "Engine: ON 🟢" else "Engine: OFF 🔴"
        tvStatus?.setTextColor(if (engineOn) Color.parseColor("#00E676") else Color.parseColor("#EF5350"))
        tvCount?.text = "Rides: $count"
        tvFilter?.text = when {
            minP > 0 && maxP > 0 -> "₹$minP–₹$maxP"
            minP > 0 -> "₹$minP+"
            maxP > 0 -> "< ₹$maxP"
            else -> "Filter: All ✓"
        }
    }
}
