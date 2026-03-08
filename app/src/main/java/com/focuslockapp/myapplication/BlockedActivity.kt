package com.focuslockapp.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class BlockedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Remove animation so the red screen appears INSTANTLY
        overridePendingTransition(0, 0)

        // --- FETCH THE END TIME FROM SETTINGS ---
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        // Note: Make sure END_HOUR and END_MIN are being saved when the user starts the lock
        val endHour = prefs.getInt("END_HOUR", 0)
        val endMin = prefs.getInt("END_MIN", 0)

        // --- CALCULATE REMAINING TIME ---
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMin = calendar.get(Calendar.MINUTE)

        val currentInMinutes = (currentHour * 60) + currentMin
        val endInMinutes = (endHour * 60) + endMin

        var durationMinutes = endInMinutes - currentInMinutes

        // Handle midnight crossover logic
        if (durationMinutes <= 0) {
            durationMinutes += 24 * 60
        }

        val hours = durationMinutes / 60
        val mins = durationMinutes % 60

        val timeString = buildString {
            if (hours > 0) append("${hours}h ")
            if (mins > 0 || hours == 0) append("${mins}m")
        }.trim()

        // 2. Build the UI programmatically
        val text = TextView(this)
        // Added the dynamic timeString into the text
        text.text = "⚠️\nFOCUS LOCK\nACTIVE\n\n$timeString remaining.\nGet back to work."
        text.textSize = 30f
        text.gravity = Gravity.CENTER
        text.setBackgroundColor(android.graphics.Color.RED)
        text.setTextColor(android.graphics.Color.WHITE)
        text.setPadding(32, 32, 32, 32) // Added padding so text doesn't touch the screen edges
        setContentView(text)

        // --- DISABLE BACK BUTTON (MODERN WAY) ---
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing. Force the user to stare at the red screen and wait.
            }
        })

        // 3. Wait 2.5 seconds before kicking them out.
        Handler(Looper.getMainLooper()).postDelayed({
            goHome()
        }, 2500)
    }


    private fun goHome() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)

        // Kill this activity so it doesn't stay in the back stack
        finishAndRemoveTask()
    }
}