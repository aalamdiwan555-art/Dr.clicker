package com.dr.clicker.atikapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dr.Clicker MainActivity
 * Flutter bridge + overlay permission helper
 * Based on Me Clicker MainActivity logic
 */
class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.example.meclicker/settings"
    private val OVERLAY_REQ = 1001

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {

                    "isAccessibilityEnabled" -> {
                        result.success(isAccessibilityEnabled())
                    }

                    "openAccessibilitySettings" -> {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(null)
                    }

                    "isOverlayPermissionGranted" -> {
                        result.success(
                            if (Build.VERSION.SDK_INT >= 23)
                                Settings.canDrawOverlays(this)
                            else
                                true
                        )
                    }

                    "requestOverlayPermission" -> {
                        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivityForResult(intent, OVERLAY_REQ)
                        }
                        result.success(null)
                    }

                    "getStats" -> {
                        val prefs = getSharedPreferences("MeClickerPrefs", MODE_PRIVATE)
                        result.success(mapOf(
                            "acceptCount" to prefs.getInt("accept_count", 0),
                            "lastLatencyMs" to prefs.getLong("last_latency_ms", 0L)
                        ))
                    }

                    "resetStats" -> {
                        getSharedPreferences("MeClickerPrefs", MODE_PRIVATE)
                            .edit()
                            .putInt("accept_count", 0)
                            .putLong("last_latency_ms", 0L)
                            .apply()
                        result.success(null)
                    }

                    "isEngineRunning" -> {
                        result.success(MyAccessibilityService.isEngineOn)
                    }

                    "getLogPath" -> {
                        val logFile = File(getExternalFilesDir(null), "god_mode_logs.txt")
                        if (!logFile.exists()) {
                            logFile.parentFile?.mkdirs()
                            val header = "--- DRCLICKER: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} ---\n" +
                                    "System Ready. Waiting for rides...\n\n"
                            try {
                                FileOutputStream(logFile).use {
                                    it.write(header.toByteArray(StandardCharsets.UTF_8))
                                }
                            } catch (_: Exception) {}
                        }
                        result.success(logFile.absolutePath)
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val target = "$packageName/${MyAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(target, ignoreCase = true) }
    }
}
