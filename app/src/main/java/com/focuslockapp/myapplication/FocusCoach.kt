package com.focuslockapp.myapplication

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FocusCoach {

    suspend fun getAdvice(context: Context, userMessage: String, userName: String, blockedCount: Int): String {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Safely grab the key from the XML Vault using the context
                val apiKey = context.getString(R.string.gemini_api_key)

                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = apiKey
                )

                // 3. Get Today's Date
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val currentDate = sdf.format(Date())

                // 4. The Smart Prompt
                val prompt = """
                    System: You are "FocusCoach".
                    CURRENT DATE: $currentDate
                    User Name: $userName
                    
                    INSTRUCTIONS:
                    1. If user chats normally, reply with short, punchy advice.
                    
                    2. If user asks to SCHEDULE or BLOCK apps, output ONLY this JSON:
                    {
                      "command": "SCHEDULE_BLOCK",
                      "reason": "Short Label (e.g. Sleep, Work, Gym, Study)", 
                      "start_date": "YYYY-MM-DD",
                      "end_date": "YYYY-MM-DD",
                      "start_time": "HH:MM",
                      "end_time": "HH:MM"
                    }
                    (Infer the 'reason' from their message. If unsure, use 'Focus').
                    
                    User Input: "$userMessage"
                """.trimIndent()

                val response = model.generateContent(prompt)

                // Clean the response
                response.text?.replace("```json", "")?.replace("```", "")?.trim()
                    ?: "Focus."

            } catch (t: Throwable) {
                "Error: I heard you, but I couldn't process that. (${t.localizedMessage})"
            }
        }
    }
}