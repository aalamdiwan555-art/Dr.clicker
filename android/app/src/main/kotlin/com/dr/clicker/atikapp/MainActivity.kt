package com.dr.clicker.atikapp

import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.dr.clicker.atikapp/bridge"

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

                    "getStats" -> {
                        val prefs = getSharedPreferences("DrClickerPrefs", MODE_PRIVATE)
                        result.success(mapOf(
                            "acceptCount" to prefs.getInt("accept_count", 0),
                            "lastLatencyMs" to prefs.getLong("last_latency_ms", 0L)
                        ))
                    }

                    "resetStats" -> {
                        getSharedPreferences("DrClickerPrefs", MODE_PRIVATE)
                            .edit()
                            .putInt("accept_count", 0)
                            .putLong("last_latency_ms", 0L)
                            .apply()
                        result.success(null)
                    }

                    "isEngineRunning" -> {
                        result.success(AutoClickService.isEngineOn)
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val target = "$packageName/${AutoClickService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(target, ignoreCase = true) }
    }
}
