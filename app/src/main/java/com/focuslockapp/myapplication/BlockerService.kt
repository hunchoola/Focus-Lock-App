package com.focuslockapp.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
// 🔥 ADDED IMPORT
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.*

class BlockerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var blockedPackages: List<String> = ArrayList()
    private var isRunning = false

    // 🔥 ADDED VARIABLE
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    // Default times
    private var startHour = 0; private var startMin = 0
    private var endHour = 0; private var endMin = 0
    private var repeatDaily = true

    // 🔴 THE AUTOMATIC KILL LIST
    private val SOCIAL_PACKAGES = listOf(
        "com.instagram.android",       // Instagram
        "com.facebook.katana",         // Facebook
        "com.zhiliaoapp.musically",    // TikTok
        "com.ss.android.ugc.trill",    // TikTok (Global)
        "com.snapchat.android",        // Snapchat
        "com.twitter.android",         // X (Twitter)
        "com.reddit.frontpage",        // Reddit
        "com.google.android.youtube",  // YouTube
        "com.netflix.mediaclient",     // Netflix
        "com.discord"                  // Discord
    )

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        // 🔥 ADDED INITIALIZATION
        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        loadConfigFromPrefs()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()

        if (intent != null) {
            val apps = intent.getStringArrayListExtra("BLOCKED_APPS")
            if (!apps.isNullOrEmpty()) blockedPackages = apps

            startHour = intent.getIntExtra("START_HOUR", startHour)
            startMin = intent.getIntExtra("START_MIN", startMin)
            endHour = intent.getIntExtra("END_HOUR", endHour)
            endMin = intent.getIntExtra("END_MIN", endMin)
        }

        if (blockedPackages.isEmpty()) {
            loadConfigFromPrefs()
        }

        if (!isRunning) {
            isRunning = true
            handler.post(checkRunnable)
        }

        return START_STICKY
    }

    private fun loadConfigFromPrefs() {
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet("saved_apps", emptySet()) ?: emptySet()
        blockedPackages = savedSet.toList()

        startHour = prefs.getInt("START_HOUR", 0)
        startMin = prefs.getInt("START_MIN", 0)
        endHour = prefs.getInt("END_HOUR", 0)
        endMin = prefs.getInt("END_MIN", 0)
        repeatDaily = prefs.getBoolean("REPEAT_DAILY", true)
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            // We run logic every 500ms
            checkAndBlock()

            if (isRunning) handler.postDelayed(this, 500)
        }
    }

    private fun checkAndBlock() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 60000, time)

        if (stats != null && stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            val currentApp = sortedStats.first().packageName

            // Never block ourselves
            if (currentApp == packageName) return

            // --- 1. DETERMINE THE ACTIVE BLOCK LIST ---
            val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            val isAiLocked = prefs.getBoolean("IS_LOCKED", false)
            val aiEndTime = prefs.getLong("LOCK_END_TIME", 0)

            val effectiveBlockedApps = ArrayList<String>()

            // A. Add Manual Apps (Always include these)
            effectiveBlockedApps.addAll(blockedPackages)

            // B. Add Social Apps (IF AI Lock is active)
            val isAiActive = isAiLocked && System.currentTimeMillis() < aiEndTime
            if (isAiActive) {
                effectiveBlockedApps.addAll(SOCIAL_PACKAGES)
            } else if (isAiLocked && System.currentTimeMillis() >= aiEndTime) {
                // Auto-turn off if time expired
                prefs.edit().putBoolean("IS_LOCKED", false).apply()
            }

            // --- 2. CHECK IF WE SHOULD BLOCK ---
            // If manual schedule is OFF and AI is OFF, do nothing
            val isManualActive = shouldBlockManualNow()

            if (!isAiActive && !isManualActive) return

            // --- 3. KILL THE APP ---
            if (effectiveBlockedApps.contains(currentApp)) {

                // Track count in Prefs
                val currentCount = prefs.getInt("BLOCK_COUNT", 0)
                prefs.edit().putInt("BLOCK_COUNT", currentCount + 1).apply()

                // 🔥 ADDED: LOG TO FIREBASE
                // We send the specific app name so you know WHICH app they tried to open
                val bundle = Bundle()
                bundle.putString("app_name", currentApp)
                firebaseAnalytics.logEvent("distraction_blocked", bundle)

                // Launch Block Screen
                val blockIntent = Intent(this, BlockedActivity::class.java)
                blockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(blockIntent)
            }
        }
    }

    private fun shouldBlockManualNow(): Boolean {
        if (startHour == 0 && endHour == 0) return true // Manual Start implies Always On until stopped

        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = startHour * 60 + startMin
        val end = endHour * 60 + endMin

        return if (start < end) {
            current in start until end
        } else {
            current >= start || current < end
        }
    }

    private fun startForegroundService() {
        val channelId = "FocusServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Focus Mode Running", NotificationManager.IMPORTANCE_LOW)
            channel.setSound(null, null)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Focus Lock Active")
            .setContentText("Monitoring distractions...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(checkRunnable)
    }
}