package com.focuslockapp.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView // ✅ Added Import for the new Share Card
import androidx.core.app.ActivityCompat
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.analytics.FirebaseAnalytics
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appList: ListView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var checkRepeat: CheckBox
    private lateinit var tvAttemptsCount: TextView

    private lateinit var mAdView: AdView
    private var mInterstitialAd: InterstitialAd? = null
    private var isStopPending = false

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    // Data Class for Apps
    data class AppItem(val name: String, val packageName: String, var isSelected: Boolean, val isSocial: Boolean)
    private val appItems = ArrayList<AppItem>()

    // List of Apps to auto-detect as "Social"
    private val socialPackages = listOf(
        "instagram", "tiktok", "zhiliaoapp", "musically",
        "facebook", "twitter", "snapchat",
        "whatsapp", "telegram", "youtube", "netflix", "pinterest",
        "reddit", "discord", "wechat"
    )

    private var startHour = 9; private var startMin = 0
    private var endHour = 17; private var endMin = 0

    // --- ALARM SCHEDULING COMPANION OBJECT ---
    companion object {
        fun rescheduleAlarms(context: Context) {
            setPreciseAlarm(context, 9, 0, 100)
        }

        private fun setPreciseAlarm(context: Context, hour: Int, minute: Int, requestCode: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ReminderReceiver::class.java)

            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val alarmInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setAlarmClock(alarmInfo, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setAlarmClock(alarmInfo, pendingIntent)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        // 1. Initialize Ads
        try {
            MobileAds.initialize(this) {}
            val adViewCheck = findViewById<AdView>(R.id.adView)
            if (adViewCheck != null) {
                mAdView = adViewCheck
                val bannerRequest = AdRequest.Builder().build()
                mAdView.loadAd(bannerRequest)
            }
            loadPunishmentAd()
        } catch (e: Exception) { e.printStackTrace() }

        // 2. NEW VIRAL SHARE BUTTON LOGIC (Replaced old btnShareSmall)
        val shareCard = findViewById<CardView>(R.id.cardDistractionShare)
        shareCard?.setOnClickListener {
            // Fetch the live number of blocked apps from SharedPreferences
            val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            val distractionsBlocked = prefs.getInt("BLOCK_COUNT", 0)

            // Dynamically get the app link
            val appPackageName = packageName
            val playStoreLink = "https://play.google.com/store/apps/details?id=$appPackageName"

            // The Viral Message
            val shareMessage = "I just blocked $distractionsBlocked distractions during my deep focus session using Focus Lock App🔒. Stop scrolling and get your work done. Can you beat my focus score?\n\nTake control of your time: $playStoreLink"

            // Trigger the Share Sheet
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareMessage)
                type = "text/plain"
            }

            val chooserIntent = Intent.createChooser(sendIntent, "Flex your Focus Score")
            startActivity(chooserIntent)
        }

        // 3. Init UI
        initUI()

        // 4. Schedule Alarms
        try {
            rescheduleAlarms(this)
            enableBootReceiver()
        } catch (e: Exception) {
            println("Alarm Error: ${e.message}")
        }
    }

    private fun enableBootReceiver() {
        val receiver = ComponentName(this, BootReceiver::class.java)
        packageManager.setComponentEnabledSetting(
            receiver,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("BLOCK_COUNT", 0)
        if (::tvAttemptsCount.isInitialized) {
            tvAttemptsCount.text = "$count"
        }
        checkAndPromptPermissions()
    }

    private fun checkAndPromptPermissions() {
        // Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                "Permission Required",
                "To block apps, Focus Lock needs to 'Appear on top'.",
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName"
            )
            return
        }

        // Usage Stats Permission
        if (!hasUsageStatsPermission()) {
            showPermissionDialog(
                "Usage Access Required",
                "To detect distractions, Focus Lock needs 'Usage Access'.",
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                null
            )
            return
        }

        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun showPermissionDialog(title: String, message: String, action: String, uriString: String?) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Enable Now") { _, _ ->
                val intent = Intent(action)
                if (uriString != null) {
                    intent.data = android.net.Uri.parse(uriString)
                }
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun initUI() {
        appList = findViewById(R.id.appList)
        btnStart = findViewById(R.id.btnStartBlock)
        btnStop = findViewById(R.id.btnStopBlock)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        checkRepeat = findViewById(R.id.checkRepeat)
        tvAttemptsCount = findViewById(R.id.tvAttemptsCount)

        // Social Select Button
        val btnSelectSocial = findViewById<Button>(R.id.btnSelectSocial)
        btnSelectSocial?.setOnClickListener {
            val isSelecting = btnSelectSocial.text.toString() == "Select Socials"
            for (i in 0 until appItems.size) {
                val item = appItems[i]
                if (item.isSocial) {
                    if (item.packageName.contains("whatsapp")) continue
                    if (item.isSelected != isSelecting) {
                        item.isSelected = isSelecting
                        appList.setItemChecked(i, isSelecting)
                    }
                }
            }
            (appList.adapter as? BaseAdapter)?.notifyDataSetChanged()
            btnSelectSocial.text = if (isSelecting) "Unselect Socials" else "Select Socials"
        }

        // AI Chat Button
        val fabChat = findViewById<FloatingActionButton>(R.id.fabChat)
        fabChat?.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        loadSavedSettings()

        btnStartTime.setOnClickListener { showTimePicker(true) }
        btnEndTime.setOnClickListener { showTimePicker(false) }

        checkRepeat.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("REPEAT_DAILY", isChecked).apply()
        }

        btnStart.setOnClickListener {
            if (!hasUsageStatsPermission() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))) {
                checkAndPromptPermissions()
                return@setOnClickListener
            }
            startBlockingService()
        }

        btnStop.setOnClickListener {
            showAdAndStop()
        }

        loadInstalledApps()
    }

    private fun loadPunishmentAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-4473657267431851/7118665351", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    mInterstitialAd = null
                    isStopPending = false
                }
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    if (isStopPending) {
                        showAdAndStop()
                        isStopPending = false
                    }
                }
            })
    }

    private fun showAdAndStop() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    stopBlockingService()
                    mInterstitialAd = null
                    loadPunishmentAd()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    stopBlockingService()
                    mInterstitialAd = null
                }
                override fun onAdShowedFullScreenContent() {
                    mInterstitialAd = null
                }
            }
            mInterstitialAd?.show(this@MainActivity)
        } else {
            isStopPending = true
            Toast.makeText(this, "Loading Ad... Please wait ⏳", Toast.LENGTH_SHORT).show()
            loadPunishmentAd()
        }
    }

    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        startHour = prefs.getInt("START_HOUR", 9)
        startMin = prefs.getInt("START_MIN", 0)
        endHour = prefs.getInt("END_HOUR", 17)
        endMin = prefs.getInt("END_MIN", 0)
        checkRepeat.isChecked = prefs.getBoolean("REPEAT_DAILY", true)
        updateTimeButton(btnStartTime, startHour, startMin, true)
        updateTimeButton(btnEndTime, endHour, endMin, false)
    }

    private fun saveTimeSettings() {
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("START_HOUR", startHour)
            putInt("START_MIN", startMin)
            putInt("END_HOUR", endHour)
            putInt("END_MIN", endMin)
            apply()
        }
    }

    private fun showTimePicker(isStart: Boolean) {
        val currentHour = if(isStart) startHour else endHour
        val currentMin = if(isStart) startMin else endMin
        val listener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            if (isStart) {
                startHour = hour
                startMin = minute
                updateTimeButton(btnStartTime, startHour, startMin, true)
            } else {
                endHour = hour
                endMin = minute
                updateTimeButton(btnEndTime, endHour, endMin, false)
            }
            saveTimeSettings()
        }
        TimePickerDialog(this, listener, currentHour, currentMin, false).show()
    }

    private fun updateTimeButton(btn: Button, hour: Int, min: Int, isStart: Boolean) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, min)
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        btn.text = (if (isStart) "Start: " else "End: ") + format.format(cal.time)
    }

    private fun startBlockingService() {
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("BLOCK_COUNT", 0).apply()
        tvAttemptsCount.text = "0"

        val selectedApps = appItems.filter { it.isSelected }.map { it.packageName }.toSet()
        prefs.edit().putStringSet("saved_apps", selectedApps).apply()

        val intent = Intent(this, BlockerService::class.java)
        intent.putStringArrayListExtra("BLOCKED_APPS", ArrayList(selectedApps))
        intent.putExtra("START_HOUR", startHour)
        intent.putExtra("START_MIN", startMin)
        intent.putExtra("END_HOUR", endHour)
        intent.putExtra("END_MIN", endMin)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // --- 1. CALCULATE REMAINING TIME FROM *CURRENT TIME* ---
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMin = calendar.get(Calendar.MINUTE)

        val currentInMinutes = (currentHour * 60) + currentMin
        val endInMinutes = (endHour * 60) + endMin

        var durationMinutes = endInMinutes - currentInMinutes

        // Handle crossing midnight (e.g., 10 PM to 6 AM)
        if (durationMinutes <= 0) {
            durationMinutes += 24 * 60
        }

        val hours = durationMinutes / 60
        val mins = durationMinutes % 60

        val timeString = buildString {
            if (hours > 0) append("${hours}h ")
            if (mins > 0 || hours == 0) append("${mins}m")
        }.trim()

        Toast.makeText(this, "Locked in for the next $timeString. Stay focused! 🔒", Toast.LENGTH_LONG).show()

        // --- 2. SCHEDULE AUTO-STOP AT END TIME ---
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val stopIntent = Intent(this, AutoStopReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 201, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopCalendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMin)
            set(Calendar.SECOND, 0)
        }

        // If the end time is earlier than right now, it means it's for tomorrow
        if (stopCalendar.timeInMillis <= System.currentTimeMillis()) {
            stopCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, stopCalendar.timeInMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, stopCalendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, stopCalendar.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun stopBlockingService() {
        val intent = Intent(this, BlockerService::class.java)
        stopService(intent)
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("IS_LOCKED", false).putLong("LOCK_END_TIME", 0).apply()
        Toast.makeText(this, "Focus Mode Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun loadInstalledApps() {
        val savedApps = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            .getStringSet("saved_apps", emptySet()) ?: emptySet()
        val pm = packageManager
        val apps = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        appItems.clear()
        for (app in apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val pkg = app.packageName
                if (pkg == "com.android.settings" || pkg == "com.android.phone" || pkg.contains("launcher") || pkg.contains("dialer")) {
                    continue
                }
                val name = app.applicationInfo?.loadLabel(pm)?.toString() ?: app.packageName
                val isSocial = socialPackages.any { pkg.contains(it) }
                val isSelected = savedApps.contains(pkg)
                appItems.add(AppItem(name, pkg, isSelected, isSocial))
            }
        }
        appItems.sortWith(compareByDescending<AppItem> { it.isSelected }
            .thenByDescending { it.isSocial }
            .thenBy { it.name })

        val adapter = object : ArrayAdapter<AppItem>(this, android.R.layout.simple_list_item_multiple_choice, appItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as CheckedTextView
                val item = getItem(position)
                view.text = item?.name
                view.isChecked = item?.isSelected == true

                if (item?.isSocial == true) {
                    // Keep your custom blue color for social apps
                    view.setTextColor(Color.parseColor("#448AFF"))
                } else {
                    // Fetch the dynamic theme color instead of hardcoded white
                    val dynamicColor = androidx.core.content.ContextCompat.getColor(context, R.color.text_primary)
                    view.setTextColor(dynamicColor)
                }
                return view
            }
        }

        appList.adapter = adapter
        appList.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in 0 until appItems.size) { appList.setItemChecked(i, appItems[i].isSelected) }
        appList.setOnItemClickListener { _, _, position, _ ->
            val item = appItems[position]
            item.isSelected = !item.isSelected
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}