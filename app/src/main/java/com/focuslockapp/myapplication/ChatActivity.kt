package com.focuslockapp.myapplication
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val messageList = ArrayList<ChatMessage>()

    private lateinit var etInput: EditText
    private lateinit var btnSend: Button

    private val GOOGLE_SCRIPT_URL by lazy { getString(R.string.sheets_webhook_url) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        MobileAds.initialize(this) {}
        val mAdView = findViewById<AdView>(R.id.adViewChat)
        mAdView.loadAd(AdRequest.Builder().build())

        recyclerView = findViewById(R.id.recyclerViewChat)
        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)

        adapter = ChatAdapter(messageList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        checkOnboardingStatus()

        btnSend.setOnClickListener {
            val msg = etInput.text.toString().trim()
            if (msg.isNotEmpty()) {
                handleUserMessage(msg)
                etInput.text.clear()
            }
        }
    }

    private fun checkOnboardingStatus() {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("USER_NAME", null)
        val userEmail = prefs.getString("USER_EMAIL", null)

        if (userName == null) {
            appendChat("FocusCoach", "Hello! I am your personal accountability partner. What should I call you?")
        } else if (userEmail == null) {
            appendChat("FocusCoach", "Welcome back, $userName! To send you progress updates, what is your email address?")
        } else {
            appendChat("FocusCoach", "Hi $userName! I'm ready. What's distracting you right now?")
        }
    }

    private fun handleUserMessage(msg: String) {
        appendChat("Me", msg)

        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val lowerMsg = msg.lowercase().trim()

        if (lowerMsg == "reset profile" || lowerMsg == "change name" || lowerMsg == "reset") {
            prefs.edit().remove("USER_NAME").remove("USER_EMAIL").apply()
            appendChat("FocusCoach", "🔄 Profile reset! Let's start over.")
            appendChat("FocusCoach", "Hello! I am your personal accountability partner. What should I call you?")
            return
        }

        val userName = prefs.getString("USER_NAME", null)
        val userEmail = prefs.getString("USER_EMAIL", null)

        lifecycleScope.launch {
            if (userName == null) {
                val cleanName = extractNameFromText(msg)
                prefs.edit().putString("USER_NAME", cleanName).apply()
                appendChat("FocusCoach", "Nice to meet you, $cleanName! What is your email address?")
                return@launch
            }

            if (userEmail == null) {
                if (isValidEmail(msg)) {
                    prefs.edit().putString("USER_EMAIL", msg).apply()
                    sendToGoogleSheet(userName, msg)
                    appendChat("FocusCoach", "All set! Tell me what you want to achieve today.")
                } else {
                    appendChat("FocusCoach", "That doesn't look like a valid email.")
                }
                return@launch
            }

            val focusPrefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            val blockedCount = focusPrefs.getInt("BLOCK_COUNT", 0)

            appendChat("FocusCoach", "Thinking...")

            val response = FocusCoach.getAdvice(this@ChatActivity, msg, userName, blockedCount)
            val lastIndex = messageList.size - 1

            if (response.trim().startsWith("{") && response.contains("SCHEDULE_BLOCK")) {
                if (lastIndex >= 0) {
                    messageList[lastIndex] = ChatMessage("Got it. Checking your schedule...", false)
                    adapter.notifyItemChanged(lastIndex)
                }
                handleSchedulingCommand(response)
            } else {
                if (lastIndex >= 0) {
                    messageList[lastIndex] = ChatMessage(response, false)
                    adapter.notifyItemChanged(lastIndex)
                }
            }
        }
    }

    private fun handleSchedulingCommand(jsonString: String) {
        try {
            val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()
            val jsonObject = JSONObject(cleanJson)

            // 1. EXTRACT ALL DETAILS
            var modeName = jsonObject.optString("reason", "Focus")
            modeName = modeName.replaceFirstChar { it.uppercase() }

            val startDate = jsonObject.optString("start_date", "Today")
            val startTime = jsonObject.optString("start_time", "Now")
            val endDate = jsonObject.optString("end_date", "Unknown")
            val endTime = jsonObject.optString("end_time", "Unknown")

            // 2. CALCULATE LOCK END TIME (Milliseconds)
            val calendar = Calendar.getInstance()

            // Try to parse the explicit End Date if available (YYYY-MM-DD)
            val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            if (endDate != "Unknown") {
                try {
                    val dateObj = sdfDate.parse(endDate)
                    if (dateObj != null) {
                        calendar.time = dateObj
                    }
                } catch (e: Exception) {
                    // Keep today's date if parsing fails
                }
            }

            // Set the End Time (HH:MM)
            val parts = endTime.split(":")
            if (parts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(Calendar.MINUTE, parts[1].toInt())
                calendar.set(Calendar.SECOND, 0)

                // If the calculated time is in the past (and no date was given), assume tomorrow
                if (calendar.timeInMillis < System.currentTimeMillis() && endDate == "Unknown") {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val lockEndTimeMillis = calendar.timeInMillis

            // 3. BUILD THE DISPLAY MESSAGE
            val confirmMessage = """
                🟢 START: 
                $startDate at $startTime
                
                🔴 END: 
                $endDate at $endTime
                
                (Apps will unlock automatically at the End time).
            """.trimIndent()

            // 4. SHOW CONFIRMATION
            AlertDialog.Builder(this)
                .setTitle("Confirm $modeName Mode? 🔒")
                .setMessage(confirmMessage) // <--- Shows full details now!
                .setPositiveButton("YES, LOCK IT") { _, _ ->

                    val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("IS_LOCKED", true)
                        .putLong("LOCK_END_TIME", lockEndTimeMillis)
                        .putString("LOCK_REASON", modeName)
                        .apply()

                    // Start the Service
                    val serviceIntent = Intent(this, BlockerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }

                    appendChat("FocusCoach", "🔒 DONE. $modeName Mode is active.")
                }
                .setNegativeButton("Cancel") { _, _ ->
                    appendChat("FocusCoach", "Okay, I cancelled the block.")
                }
                .show()

        } catch (e: Exception) {
            appendChat("FocusCoach", "I tried to schedule that, but I got confused. Try again?")
        }
    }

    private suspend fun sendToGoogleSheet(name: String, email: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(GOOGLE_SCRIPT_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                val json = JSONObject().put("name", name).put("email", email)
                conn.outputStream.write(json.toString().toByteArray())
                conn.responseCode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun appendChat(sender: String, message: String) {
        val isUser = sender == "Me"
        messageList.add(ChatMessage(message, isUser))
        adapter.notifyItemInserted(messageList.size - 1)
        recyclerView.scrollToPosition(messageList.size - 1)
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun extractNameFromText(input: String): String {
        return input.replace("call me ", "", true)
            .replace("my name is ", "", true).trim()
    }
}