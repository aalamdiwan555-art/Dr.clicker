package com.dr.clicker.atikapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Auto-restart Dr.Clicker accessibility service on boot and power events.
 * Android 6+ requires user to manually enable once; then this keeps it alive.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DrClickerBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "BootReceiver: $action received")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT" -> {
                // Try to start the accessibility service
                val serviceIntent = Intent(context, MyAccessibilityService::class.java)
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.i(TAG, "Service auto-started after $action")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service: ${e.message}")
                }
            }
        }
    }
}
