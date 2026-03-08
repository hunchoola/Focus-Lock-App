package com.focuslockapp.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Phone just restarted. We must Reschedule the alarms!
            val mainActivity = MainActivity()
            // We call a static helper (we will create this in MainActivity next)
            MainActivity.rescheduleAlarms(context)
        }
    }
}