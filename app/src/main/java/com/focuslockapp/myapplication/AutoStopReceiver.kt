package com.focuslockapp.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 1. Stop the BlockerService and kill the notification
        val serviceIntent = Intent(context, BlockerService::class.java)
        context.stopService(serviceIntent)

        // 2. Reset the lock status in SharedPreferences
        val prefs = context.getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("IS_LOCKED", false).putLong("LOCK_END_TIME", 0).apply()
    }
}