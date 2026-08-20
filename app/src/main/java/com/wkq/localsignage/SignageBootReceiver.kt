package com.wkq.localsignage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class SignageBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action in SUPPORTED_BOOT_ACTIONS) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, SignageService::class.java))
            }.onFailure { error ->
                Log.w(TAG, "Unable to restore signage service after $action", error)
            }
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to restore signage activity after $action", error)
            }
        }
    }

    companion object {
        private val SUPPORTED_BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
        const val TAG = "SignageBootReceiver"
    }
}
