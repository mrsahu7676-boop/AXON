package com.axon.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import com.axon.assistant.models.AxonAction
import com.axon.assistant.models.NotifItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Executes all 18 device actions parsed from Gemini's JSON response.
 */
class ActionExecutor(
    private val context: Context,
    private val contactResolver: ContactResolver,
    private val ttsManager: TTSManager
) {

    // App name → package name map
    private val appMap = mapOf(
        "whatsapp"   to "com.whatsapp",
        "youtube"    to "com.google.android.youtube",
        "chrome"     to "com.android.chrome",
        "instagram"  to "com.instagram.android",
        "spotify"    to "com.spotify.music",
        "maps"       to "com.google.android.apps.maps",
        "gmail"      to "com.google.android.gm",
        "facebook"   to "com.facebook.katana",
        "twitter"    to "com.twitter.android",
        "x"          to "com.twitter.android",
        "telegram"   to "org.telegram.messenger",
        "snapchat"   to "com.snapchat.android",
        "netflix"    to "com.netflix.mediaclient",
        "phonepe"    to "com.phonepe.app",
        "gpay"       to "com.google.android.apps.nbu.paisa.user",
        "paytm"      to "net.one97.paytm",
        "zomato"     to "com.application.zomato",
        "swiggy"     to "in.swiggy.android",
        "uber"       to "com.ubercab",
        "ola"        to "com.olacabs.customer",
        "calculator" to "com.google.android.calculator",
        "clock"      to "com.google.android.deskclock",
        "contacts"   to "com.android.contacts",
        "camera"     to "android.media.action.IMAGE_CAPTURE",
        "settings"   to "com.android.settings"
    )

    /**
     * Execute an action and return a status string (used for error feedback).
     */
    fun execute(action: AxonAction) {
        val params = action.params ?: emptyMap()
        when (action.type) {
            "MAKE_CALL"           -> makeCall(params)
            "SEND_SMS"            -> sendSms(params)
            "OPEN_APP"            -> openApp(params)
            "SET_ALARM"           -> setAlarm(params)
            "PLAY_MUSIC"          -> playMusic(params)
            "TAKE_PHOTO"          -> takePhoto()
            "TOGGLE_WIFI"         -> toggleWifi(params)
            "TOGGLE_BLUETOOTH"    -> toggleBluetooth(params)
            "SET_VOLUME"          -> setVolume(params)
            "TOGGLE_FLASHLIGHT"   -> toggleFlashlight(params)
            "SEARCH_WEB"          -> searchWeb(params)
            "SEND_WHATSAPP"       -> sendWhatsApp(params)
            "GET_BATTERY"         -> getBattery()
            "READ_NOTIFICATIONS"  -> readNotifications()
            "SET_REMINDER"        -> setReminder(params)
            "OPEN_SETTINGS"       -> openSettings(params)
            "INCREASE_BRIGHTNESS" -> setBrightness(params)
            "SPEAK_TIME"          -> speakTime()
            "SPEAK_DATE"          -> speakDate()
        }
    }

    // ─── Action Implementations ───────────────────────────────────────────────

    private fun makeCall(params: Map<String, Any>) {
        val contact = params["contact"]?.toString() ?: return
        val number  = contactResolver.resolveNumber(contact)
        val uri     = if (number != null) {
            Uri.parse("tel:${contactResolver.toDialable(number)}")
        } else {
            Uri.parse("tel:$contact") // fallback: treat as raw number
        }
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            ttsManager.speak("Call nahi ho saka. Permission check karo.")
        }
    }

    private fun sendSms(params: Map<String, Any>) {
        val contact = params["contact"]?.toString() ?: return
        val message = params["message"]?.toString() ?: return
        val number  = contactResolver.resolveNumber(contact)
            ?: contactResolver.toDialable(contact)
        try {
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(number, null, message, null, null)
        } catch (e: Exception) {
            ttsManager.speak("SMS nahi bheja ja saka.")
        }
    }

    private fun openApp(params: Map<String, Any>) {
        val appName = params["appName"]?.toString()?.lowercase() ?: return
        val packageName = appMap[appName]

        val intent = if (packageName != null) {
            if (packageName.contains(".action.") || packageName == "android.media.action.IMAGE_CAPTURE") {
                Intent(packageName).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            } else {
                context.packageManager.getLaunchIntentForPackage(packageName)
                    ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    ?: run {
                        // App not installed — open Play Store
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=$packageName")).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    }
            }
        } else {
            // Try direct package search
            context.packageManager.getLaunchIntentForPackage(appName)
                ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        }

        try {
            if (intent != null) context.startActivity(intent)
            else ttsManager.speak("$appName nahi mila device pe.")
        } catch (e: Exception) {
            ttsManager.speak("$appName open nahi ho saka.")
        }
    }

    private fun setAlarm(params: Map<String, Any>) {
        val hour   = (params["hour"]   as? Number)?.toInt() ?: return
        val minute = (params["minute"] as? Number)?.toInt() ?: 0
        val label  = params["label"]?.toString() ?: "AXON Alarm"
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            ttsManager.speak("Alarm set nahi ho saka.")
        }
    }

    private fun playMusic(params: Map<String, Any>) {
        val query = params["query"]?.toString() ?: return
        // Try Spotify first, then YouTube Music, then Google
        val spotifyIntent = Intent(Intent.ACTION_VIEW,
            Uri.parse("spotify:search:$query")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val webIntent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(spotifyIntent)
        } catch (e: Exception) {
            try { context.startActivity(webIntent) } catch (e2: Exception) {
                ttsManager.speak("Music nahi chal saka.")
            }
        }
    }

    private fun takePhoto() {
        val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            ttsManager.speak("Camera open nahi ho saka.")
        }
    }

    private fun toggleWifi(params: Map<String, Any>) {
        val state = params["state"]?.toString() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — open WiFi settings
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = (state == "on")
        }
    }

    private fun toggleBluetooth(params: Map<String, Any>) {
        // Android 12+ removed enable()/disable() — open settings instead
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            ttsManager.speak("Bluetooth settings open nahi ho saka.")
        }
    }

    private fun setVolume(params: Map<String, Any>) {
        val level = (params["level"] as? Number)?.toInt() ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val clamped = level.coerceIn(0, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
    }

    private fun toggleFlashlight(params: Map<String, Any>) {
        val state = params["state"]?.toString() ?: return
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            cameraManager.setTorchMode(cameraId, state == "on")
        } catch (e: Exception) {
            ttsManager.speak("Flashlight toggle nahi hua.")
        }
    }

    private fun searchWeb(params: Map<String, Any>) {
        val query = params["query"]?.toString() ?: return
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            ttsManager.speak("Search nahi ho saka.")
        }
    }

    private fun sendWhatsApp(params: Map<String, Any>) {
        val contact = params["contact"]?.toString() ?: return
        val message = params["message"]?.toString() ?: ""
        val number  = contactResolver.resolveWhatsAppNumber(contact) ?: contact
        val intent  = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(message)}")
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        try { context.startActivity(intent) } catch (e: Exception) {
            ttsManager.speak("WhatsApp open nahi hua. Install hai kya?")
        }
    }

    private fun getBattery() {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = bm.isCharging
        val status = if (charging) "aur charging ho raha hai" else ""
        ttsManager.speak("Battery $level percent hai $status.")
    }

    private fun readNotifications() {
        val notifs = NotificationService.getRecentNotifications()
        if (notifs.isEmpty()) {
            ttsManager.speak("Koi nayi notification nahi hai.")
            return
        }
        val text = buildString {
            append("${notifs.size} notifications hain. ")
            notifs.take(5).forEach { n ->
                append("${n.app}: ${n.title}. ")
            }
        }
        ttsManager.speak(text)
    }

    private fun setReminder(params: Map<String, Any>) {
        val text  = params["text"]?.toString() ?: return
        val time  = params["time"]?.toString() ?: ""
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, "$text — $time")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try { context.startActivity(intent) } catch (e: Exception) {
            ttsManager.speak("Reminder set nahi ho saka.")
        }
    }

    private fun openSettings(params: Map<String, Any>) {
        val section = params["section"]?.toString()?.lowercase() ?: ""
        val action = when {
            section.contains("wifi")        -> Settings.ACTION_WIFI_SETTINGS
            section.contains("bluetooth")   -> Settings.ACTION_BLUETOOTH_SETTINGS
            section.contains("display")     -> Settings.ACTION_DISPLAY_SETTINGS
            section.contains("sound")       -> Settings.ACTION_SOUND_SETTINGS
            section.contains("battery")     -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            section.contains("app")         -> Settings.ACTION_APPLICATION_SETTINGS
            section.contains("notif")       -> Settings.ACTION_APP_NOTIFICATION_SETTINGS
            section.contains("location")    -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            section.contains("storage")     -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            else                            -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        try { context.startActivity(intent) } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    private fun setBrightness(params: Map<String, Any>) {
        val level = (params["level"] as? Number)?.toInt() ?: return
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    level.coerceIn(0, 255)
                )
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                ttsManager.speak("Brightness set karne ke liye permission do.")
            }
        } catch (e: Exception) {
            ttsManager.speak("Brightness change nahi ho saka.")
        }
    }

    private fun speakTime() {
        val sdf  = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val time = sdf.format(Date())
        ttsManager.speak("Abhi time hai $time.")
    }

    private fun speakDate() {
        val sdf  = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
        val date = sdf.format(Date())
        ttsManager.speak("Aaj hai $date.")
    }
}
