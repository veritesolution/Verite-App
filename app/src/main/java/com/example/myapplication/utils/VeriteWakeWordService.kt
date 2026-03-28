package com.example.myapplication.utils

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.MindSetActivity
import com.example.myapplication.R
import java.util.*

/**
 * Foreground Service for 'Hey Verite' wake-word detection.
 * Listens continuously for "Verite" to trigger active voice commands.
 */
class VeriteWakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val TAG = "VeriteWakeWord"
    private val CHANNEL_ID = "wake_word_service"
    private val NOTIF_ID = 1001

    /**
     * Initializes the notification channel and starts the foreground service.
     */
    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            
            val notification = createNotification("Listening for 'Hey Verite'...")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            
            initializeRecognizer()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VeriteWakeWordService", e)
            stopSelf()
        }
    }

    private fun initializeRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition NOT available on this device")
            stopSelf()
            return
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission NOT granted. Service cannot function.")
            stopSelf()
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(WakeWordListener())
            }
            startListening()
            Log.i(TAG, "SpeechRecognizer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechRecognizer: ${e.message}")
            stopSelf()
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Limit duration for wake-word sensing
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        speechRecognizer?.startListening(intent)
        Log.d(TAG, "Started listening for wake-word")
    }

    private inner class WakeWordListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            // Restart on error to keep listening (standard for wake-word sensing)
            Log.d(TAG, "Recognizer error: $error. Restarting...")
            Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 500)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase() ?: ""
            checkForWakeWord(text)
            startListening() // Keep listening
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.lowercase() ?: ""
            if (checkForWakeWord(text)) {
                speechRecognizer?.stopListening()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun checkForWakeWord(text: String): Boolean {
        if (text.contains("verite") || text.contains("very") || text.contains("hey")) {
            Log.i(TAG, "Wake-word DETECTED: $text")
            triggerActivation()
            return true
        }
        return false
    }

    /**
     * Triggers the voice assistant activation by providing haptic feedback
     * and bringing the [MindSetActivity] to the foreground.
     */
    private fun triggerActivation() {
        // 1. Notify user with vibration
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(android.os.VibratorManager::class.java)
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }

        // 2. Bring Activity to foreground or send broadcast
        val intent = Intent(this, MindSetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra("ACTIVATE_VOICE", true)
        }
        startActivity(intent)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MindSetActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vérité Voice Assistant")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Wake-Word Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Stop service if the app is swiped away
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
