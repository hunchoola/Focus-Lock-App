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
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.floatingactionbutton.FloatingActionButton
// --- FIREBASE IMPORTS FIXED ---
import com.google.firebase.FirebaseApp
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

    data class AppItem(val name: String, val packageName: String, var isSelected: Boolean, val isSocial: Boolean)
    private val appItems = ArrayList<AppItem>()

    private val socialPackages = listOf(
        "instagram", "tiktok", "zhiliaoapp", "musically",
        "facebook", "twitter", "snapchat",
        "whatsapp", "telegram", "youtube", "netflix", "pinterest",
        "reddit", "discord", "wechat"
    )

    private var startHour = 9; private var startMin = 0
    private var endHour = 17; private var endMin = 0

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

        // 1. FORCE FIREBASE INITIALIZATION FIRST
        try {
            FirebaseApp.initializeApp(this)
            firebaseAnalytics = FirebaseAnalytics.getInstance(this)
            // Log a test event immediately to verify connection
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "App Launched")
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, bundle)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContentView(R.layout.activity_main)

        // 2. Initialize Ads
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

        // 3. Share Button Logic
        val shareCard = findViewById<CardView>(R.id.cardDistractionShare)
        shareCard?.setOnClickListener {
            val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
            val distractionsBlocked = prefs.getInt("BLOCK_COUNT", 0)
            val playStoreLink = "https://play.google.com/store/apps/details?id=$packageName"
            val shareMessage = "I just blocked $distractionsBlocked distractions using Focus Lock App🔒. Can you beat my focus score?\n\n$playStoreLink"

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareMessage)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, "Flex your Focus Score"))

            // Log Share Event to Firebase
            firebaseAnalytics.logEvent("share_score_clicked", null)
        }

        initUI()

        try {
            rescheduleAlarms(this)
            enableBootReceiver()
        } catch (e: Exception) {
            e.printStackTrace()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog("Permission Required", "To block apps, Focus Lock needs 'Appear on top'.", Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName")
            return
        }
        if (!hasUsageStatsPermission()) {
            showPermissionDialog("Usage Access Required", "To detect distractions, Focus Lock needs 'Usage Access'.", Settings.ACTION_USAGE_ACCESS_SETTINGS, null)
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun showPermissionDialog(title: String, message: String, action: String, uriString: String?) {
        AlertDialog.Builder(this)
            .setTitle(title).setMessage(message).setCancelable(false)
            .setPositiveButton("Enable Now") { _, _ ->
                val intent = Intent(action)
                if (uriString != null) intent.data = android.net.Uri.parse(uriString)
                startActivity(intent)
            }
            .setNegativeButton("Later", null).show()
    }

    private fun initUI() {
        appList = findViewById(R.id.appList)
        btnStart = findViewById(R.id.btnStartBlock)
        btnStop = findViewById(R.id.btnStopBlock)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        checkRepeat = findViewById(R.id.checkRepeat)
        tvAttemptsCount = findViewById(R.id.tvAttemptsCount)

        findViewById<Button>(R.id.btnSelectSocial)?.setOnClickListener {
            val isSelecting = (it as Button).text.toString() == "Select Socials"
            for (i in 0 until appItems.size) {
                if (appItems[i].isSocial && !appItems[i].packageName.contains("whatsapp")) {
                    appItems[i].isSelected = isSelecting
                    appList.setItemChecked(i, isSelecting)
                }
            }
            (appList.adapter as? BaseAdapter)?.notifyDataSetChanged()
            it.text = if (isSelecting) "Unselect Socials" else "Select Socials"
        }

        findViewById<FloatingActionButton>(R.id.fabChat)?.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        loadSavedSettings()
        btnStartTime.setOnClickListener { showTimePicker(true) }
        btnEndTime.setOnClickListener { showTimePicker(false) }

        checkRepeat.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE).edit().putBoolean("REPEAT_DAILY", isChecked).apply()
        }

        btnStart.setOnClickListener {
            if (!hasUsageStatsPermission() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))) {
                checkAndPromptPermissions()
            } else {
                startBlockingService()
                firebaseAnalytics.logEvent("focus_mode_started", null)
            }
        }

        btnStop.setOnClickListener { showAdAndStop() }
        loadInstalledApps()
    }

    private fun loadPunishmentAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-4473657267431851/7118665351", adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) { mInterstitialAd = null; isStopPending = false }
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    if (isStopPending) { showAdAndStop(); isStopPending = false }
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
                override fun onAdFailedToShowFullScreenContent(adError: AdError) { stopBlockingService(); mInterstitialAd = null }
            }
            mInterstitialAd?.show(this)
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
        getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE).edit().apply {
            putInt("START_HOUR", startHour)
            putInt("START_MIN", startMin)
            putInt("END_HOUR", endHour)
            putInt("END_MIN", endMin)
            apply()
        }
    }

    private fun showTimePicker(isStart: Boolean) {
        val listener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            if (isStart) { startHour = hour; startMin = minute; updateTimeButton(btnStartTime, startHour, startMin, true) }
            else { endHour = hour; endMin = minute; updateTimeButton(btnEndTime, endHour, endMin, false) }
            saveTimeSettings()
        }
        TimePickerDialog(this, listener, if(isStart) startHour else endHour, if(isStart) startMin else endMin, false).show()
    }

    private fun updateTimeButton(btn: Button, hour: Int, min: Int, isStart: Boolean) {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min) }
        btn.text = (if (isStart) "Start: " else "End: ") + SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
    }

    private fun startBlockingService() {
        val prefs = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("BLOCK_COUNT", 0).apply()
        tvAttemptsCount.text = "0"

        val selectedApps = appItems.filter { it.isSelected }.map { it.packageName }.toSet()
        prefs.edit().putStringSet("saved_apps", selectedApps).apply()

        val intent = Intent(this, BlockerService::class.java).apply {
            putStringArrayListExtra("BLOCKED_APPS", ArrayList(selectedApps))
            putExtra("START_HOUR", startHour)
            putExtra("START_MIN", startMin)
            putExtra("END_HOUR", endHour)
            putExtra("END_MIN", endMin)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)

        Toast.makeText(this, "Focus Mode Active! 🔒", Toast.LENGTH_SHORT).show()

        // --- Schedule Auto-Stop ---
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val stopIntent = Intent(this, AutoStopReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 201, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val stopCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMin)
            set(Calendar.SECOND, 0)
        }
        if (stopCalendar.timeInMillis <= System.currentTimeMillis()) stopCalendar.add(Calendar.DAY_OF_YEAR, 1)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, stopCalendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, stopCalendar.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun stopBlockingService() {
        stopService(Intent(this, BlockerService::class.java))
        getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE).edit().putBoolean("IS_LOCKED", false).apply()
        Toast.makeText(this, "Focus Mode Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun loadInstalledApps() {
        val savedApps = getSharedPreferences("FocusPrefs", Context.MODE_PRIVATE).getStringSet("saved_apps", emptySet()) ?: emptySet()
        val pm = packageManager
        val apps = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        appItems.clear()
        for (app in apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val pkg = app.packageName
                if (pkg == "com.android.settings" || pkg == "com.android.phone" || pkg.contains("launcher")) continue
                val name = app.applicationInfo?.loadLabel(pm)?.toString() ?: pkg
                val isSocial = socialPackages.any { pkg.contains(it) }
                appItems.add(AppItem(name, pkg, savedApps.contains(pkg), isSocial))
            }
        }
        appItems.sortWith(compareByDescending<AppItem> { it.isSelected }.thenByDescending { it.isSocial }.thenBy { it.name })

        appList.adapter = object : ArrayAdapter<AppItem>(this, android.R.layout.simple_list_item_multiple_choice, appItems) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as CheckedTextView
                val item = getItem(position)
                view.text = item?.name
                view.isChecked = item?.isSelected == true
                view.setTextColor(if (item?.isSocial == true) Color.parseColor("#448AFF") else Color.BLACK)
                return view
            }
        }
        appList.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        for (i in 0 until appItems.size) appList.setItemChecked(i, appItems[i].isSelected)
        appList.setOnItemClickListener { _, _, position, _ -> appItems[position].isSelected = !appItems[position].isSelected }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        else appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
}