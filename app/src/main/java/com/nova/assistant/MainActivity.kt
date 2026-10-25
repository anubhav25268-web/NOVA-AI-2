package com.nova.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.*

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(60, 60, 60, 60)
        }

        val title = TextView(this).apply {
            text = "NOVA AI"
            textSize = 32f
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val statusText = TextView(this).apply {
            text = "आपका पर्सनल वॉइस असिस्टेंट\n('Nova' बोलकर कमांड दें: टॉर्च ऑन/ऑफ, अलार्म, यूट्यूब, टाइम)"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 60)
        }

        val startBtn = Button(this).apply {
            text = "START NOVA BACKGROUND AI"
            textSize = 16f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0284C7"))
                cornerRadius = 30f
            }
            setPadding(40, 30, 40, 30)
            setOnClickListener {
                checkPermissionsAndStart()
            }
        }

        val accessBtn = Button(this).apply {
            text = "ENABLE ACCESSIBILITY"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 30f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 40 }
            layoutParams = params
            setPadding(40, 25, 40, 25)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(startBtn)
        layout.addView(accessBtn)
        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(perms, 101)
        }
        val serviceIntent = Intent(this, NovaVoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}

class NovaVoiceService : Service(), TextToSpeech.OnInitListener {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        startForegroundServiceNotification()
        initSpeechRecognizer()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "nova_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NOVA AI Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("NOVA AI Active")
                .setContentText("Listening in background...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build()
            startForeground(1, notification)
        }
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    restartListening(intent)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        handleCommand(matches[0].lowercase(Locale.getDefault()))
                    }
                    restartListening(intent)
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }
    }

    private fun restartListening(intent: Intent) {
        mainHandler.postDelayed({
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {}
        }, 1000)
    }

    private fun handleCommand(cmd: String) {
        when {
            cmd.contains("torch on") || cmd.contains("लाइट चालू") || cmd.contains("टॉर्च चालू") -> {
                toggleTorch(true)
                speak("टॉर्च चालू कर दी गई है")
            }
            cmd.contains("torch off") || cmd.contains("लाइट बंद") || cmd.contains("टॉर्च बंद") -> {
                toggleTorch(false)
                speak("टॉर्च बंद कर दी गई है")
            }
            cmd.contains("time") || cmd.contains("समय") || cmd.contains("टाइम") -> {
                val cal = Calendar.getInstance()
                val h = cal.get(Calendar.HOUR)
                val m = cal.get(Calendar.MINUTE)
                speak("अभी समय $h बजकर $m मिनट हुआ है")
            }
            cmd.contains("youtube") || cmd.contains("यूट्यूब") -> {
                speak("यूट्यूब खोला जा रहा है")
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i)
            }
            cmd.contains("alarm") || cmd.contains("अलार्म") -> {
                speak("सुबह 7 बजे का अलार्म सेट किया जा रहा है")
                val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, 7)
                    putExtra(AlarmClock.EXTRA_MINUTES, 0)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i)
            }
            cmd.contains("nova") || cmd.contains("नोवा") || cmd.contains("hello") -> {
                speak("जी बॉस, बताइये मैं क्या मदद करूँ?")
            }
        }
    }

    private fun toggleTorch(state: Boolean) {
        try {
            val camManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camManager.cameraIdList[0]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                camManager.setTorchMode(cameraId, state)
            }
        } catch (e: Exception) {}
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NOVA_ID")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            speak("नोवा एआई चालू हो गया है")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}

class NovaAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
