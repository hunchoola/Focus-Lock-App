package com.focuslockapp.myapplication

import android.content.Context
import android.content.Intent

object ShareUtils {

    fun shareStatus(context: Context, minutesFocused: Int, isSuccess: Boolean) {

        val appLink = "https://play.google.com/store/apps/details?id=${context.packageName}"

        // A list of high-converting messages
        val messages = listOf(
            // 1. The "Life Hack" Approach
            """
            Finally found a way to stop the mindless doom-scrolling. 🛑📱
            
            Focus Lock App actually forces me to get work done. If you get distracted easily, you need this.
            
            Get it here:
            $appLink
            """.trimIndent(),

            // 2. The "Productivity Flex" Approach
            """
            My focus is finally back. 🦁
            
            Just locked in for deep work with zero distractions. No Instagram, no noise. Only progress.
            
            Get Focus Lock App here:
            $appLink
            """.trimIndent(),

            // 3. The "Direct Challenge" Approach
            """
            Stop scrolling. Start building. 🧱
            
            I use Focus Lock App to reclaim my time from social media. Try it and see how much you get done today.
            
            Download:
            $appLink
            """.trimIndent(),

            // 4. The "Secret Weapon" Approach
            """
            Productivity Hack: Block the apps that waste your time before they steal your day. ⏳
            
            Focus Lock App is a game changer for my schedule.
            
            Try it:
            $appLink
            """.trimIndent()
        )

        // PICK A RANDOM ONE
        val randomMessage = messages.random()

        // Create the Share Intent
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, randomMessage)

        // Target WhatsApp
        intent.setPackage("com.whatsapp")

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            intent.setPackage(null)
            context.startActivity(Intent.createChooser(intent, "Share via..."))
        }
    }
}