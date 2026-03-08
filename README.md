# Focus Lock App 🔒 🤖

A strict, AI-powered productivity app for Android designed to enforce deep work and eliminate digital distractions.

Focus Lock goes beyond standard screen-time limits. It physically blocks distracting apps using Android Overlay Services and features an integrated AI accountability coach (powered by Gemini 2.5 Flash) to help users stay on track and reflect on their habits.

## 🚀 Features
* **Aggressive App Blocking:** Instantly detects and overlays a lock screen when a user attempts to open a blacklisted app (TikTok, Instagram, etc.).
* **AI Focus Coach:** An integrated chatbot that acts as a tough-love accountability partner, powered by Google's Gemini 2.5 Flash model.
* **Smart Scheduling:** The AI can automatically parse natural language (e.g., "I need to study for 2 hours") and schedule a system-wide app block.
* **Cloud Analytics:** Automatically logs user commitment data to a secure Google Sheet backend.
* **Monetization:** Fully integrated with Google AdMob (Banner and Interstitial ads).

## 🛠️ Tech Stack
* **Language:** Kotlin
* **AI Integration:** Google Generative AI SDK (Gemini 2.5 Flash)
* **Backend:** Google Sheets API Webhook & Firebase Analytics
* **Android APIs:** `UsageStatsManager`, Foreground Services, `System_Alert_Window`
* **Concurrency:** Kotlin Coroutines & Dispatchers

## 🔒 Security & Local Setup
To protect API quotas, the sensitive keys have been removed from this public repository. If you are cloning this project to run locally, you must provide your own keys.

1. Clone the repository.
2. Navigate to `app/src/main/res/values/`.
3. Create a new file named `secrets.xml`.
4. Add your Gemini API key and Google Sheets Webhook URL:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="sheets_webhook_url">YOUR_GOOGLE_SHEETS_WEBHOOK_URL</string>
    <string name="gemini_api_key">YOUR_GEMINI_API_KEY</string>
</resources>