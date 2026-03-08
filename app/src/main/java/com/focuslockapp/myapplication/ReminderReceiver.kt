package com.focuslockapp.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // 1. Pick a Random Quote
        val quotes = listOf(
            // The Universal / Focus Quotes
            "Social media won't pay your rent. Open the app.",
            "You have goals. Your phone is killing them. Let's focus.",
            "Are you productive today, or just busy? Check in.",
            "Time is ticking. Log your focus hours now.",
            "Don't let the algorithm control your day. Lock in.",

            // The Deep Work / Professional Quotes
            "Great work requires deep focus. Cheap dopamine does not.",
            "Deadlines do not move just because you got distracted. Get back to work.",
            "Are you building your future, or just watching someone else's?",
            "The algorithm is getting richer while you get distracted. Take your time back.",
            "You are one focused session away from a breakthrough. Drop the phone.",

            // The "Tough Love" General Quotes
            "Motivation is a lie. Discipline is the only way. Activate the lock.",
            "You said you were going to work 30 minutes ago. Open the app.",
            "The pain of discipline weighs ounces. The pain of regret weighs tons. Pick one.",
            "No one is coming to save your deadlines. It is entirely on you.",
            "Are you going to let a piece of glass and plastic ruin your goals today?"
        )
        val randomQuote = quotes[Random.nextInt(quotes.size)]

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "DailyReminderChannel"

        // Create Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Daily Focus Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // 2. 🟢 CHANGED: Redirect to MainActivity (Home Screen)
        val contentIntent = Intent(context, MainActivity::class.java)

        // Add flags to ensure it opens fresh or brings existing to front
        contentIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

        val pendingIntent = PendingIntent.getActivity(
            context, 100, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Build Notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // Ensure this icon exists, or use R.mipmap.ic_launcher
            .setContentTitle("FocusCoach 🤖")
            .setContentText(randomQuote)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Removes notification when clicked
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(200, notification)

        // 4. Reschedule for Tomorrow (Loop)
        MainActivity.rescheduleAlarms(context)
    }
}