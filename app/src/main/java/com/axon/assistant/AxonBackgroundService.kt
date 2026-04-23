package com.axon.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * AXON Foreground Service to keep the assistant alive 24/7.
 */
class AxonBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var voiceInputManager: VoiceInputManager
    private lateinit var geminiClient: GeminiAPIClient
    private lateinit var ttsManager: TTSManager
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var moodDetector: MoodDetector
    private lateinit var history: ConversationHistory

    override fun onCreate() {
        super.onCreate()
        initAxon()
    }

    private fun initAxon() {
        history = ConversationHistory()
        geminiClient = GeminiAPIClient(this, history)
        moodDetector = MoodDetector()
        
        ttsManager = TTSManager(this, 
            onSpeakStart = { updateNotification("Axon is Speaking...") },
            onSpeakDone = { 
                updateNotification("Axon is Listening...")
                voiceInputManager.startListening() 
            }
        )

        actionExecutor = ActionExecutor(this, ContactResolver(this), ttsManager)

        voiceInputManager = VoiceInputManager(this,
            onTranscript = { text, duration -> processVoice(text, duration) },
            onPartialResult = { /* No-op for background */ },
            onListeningStarted = { updateNotification("Axon is Listening...") },
            onListeningEnded = { /* No-op */ },
            onError = { msg -> 
                // Restart listening on error (like timeout)
                if (msg.contains("timeout", true) || msg.contains("sun nahi aaya", true)) {
                    voiceInputManager.startListening()
                }
            }
        )
        voiceInputManager.initialize()
    }

    private fun processVoice(text: String, duration: Long) {
        if (text.isBlank()) {
            voiceInputManager.startListening()
            return
        }

        updateNotification("Axon is Thinking...")
        val mood = moodDetector.detect(text, duration)
        
        geminiClient.sendMessage(text, mood,
            onSuccess = { response ->
                ttsManager.speak(response.spoken)
                response.action?.let { actionExecutor.execute(it) }
            },
            onError = { err ->
                ttsManager.speak("Sorry, connection issue.")
                voiceInputManager.startListening()
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, buildNotification("Axon is Active"))
        acquireWakeLock()
        
        voiceInputManager.startListening()

        return START_STICKY
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Axon::BackgroundListening")
        wakeLock?.acquire()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "AXON_CHANNEL", "AXON Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "AXON_CHANNEL")
            .setContentTitle("AXON AI")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(content))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        voiceInputManager.destroy()
        ttsManager.destroy()
        wakeLock?.release()
        super.onDestroy()
    }
}
