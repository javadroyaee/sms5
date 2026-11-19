package ir.jrweb.banksms

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class PopupService : Service() {

    private var windowManager: WindowManager? = null
    private var popupView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val closeRunnable = Runnable {
        removePopup()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = intent?.getStringExtra("TYPE") ?: return START_NOT_STICKY
        val amount = intent.getStringExtra("AMOUNT") ?: return START_NOT_STICKY

        wakeUpScreen()
        playSoftSound(type)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (km.isKeyguardLocked || !pm.isInteractive) {
            try {
                val lockIntent = Intent(this, LockPopupActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra("TYPE", type)
                    putExtra("AMOUNT", amount)
                }
                startActivity(lockIntent)
            } catch (_: Exception) {
            }
        }

        showCenterPopup(type, amount)
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun wakeUpScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "JRwebBankSMS::WakeLock"
            )
            wakeLock.acquire(15_000L)
        } catch (_: Exception) {
        }
    }

    private fun playSoftSound(type: String) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)
            if (type == "DEPOSIT") {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
                handler.postDelayed({
                    toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 130)
                    handler.postDelayed({ toneGen.release() }, 200)
                }, 110)
            } else {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                handler.postDelayed({ toneGen.release() }, 200)
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showCenterPopup(type: String, amount: String) {
        removePopup()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        popupView = inflater.inflate(R.layout.popup_overlay, null)

        val container = popupView!!.findViewById<LinearLayout>(R.id.popupContainer)
        val tvAmount = popupView!!.findViewById<TextView>(R.id.tvPopupAmount)

        val bgDrawable = if (type == "DEPOSIT") {
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1E9E49"), Color.parseColor("#136F32"))
            ).apply {
                cornerRadius = 56f
                setStroke(3, Color.parseColor("#66FFFFFF"))
            }
        } else {
            GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#F56A00"), Color.parseColor("#C84600"))
            ).apply {
                cornerRadius = 56f
                setStroke(3, Color.parseColor("#66FFFFFF"))
            }
        }
        container.background = bgDrawable
        tvAmount.text = amount

        container.scaleX = 0.88f
        container.scaleY = 0.88f
        container.alpha = 0f
        container.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(220)
            .start()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        container.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    val dist = maxOf(abs(dx), abs(dy))
                    view.alpha = (1f - (dist / 650f)).coerceIn(0.25f, 1f)
                    try {
                        windowManager?.updateViewLayout(popupView, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - touchStartX)
                    val dy = abs(event.rawY - touchStartY)
                    if (dx > 120 || dy > 120 || (dx < 15 && dy < 15)) {
                        removePopup()
                        stopSelf()
                    } else {
                        params.x = 0
                        params.y = 0
                        view.alpha = 1f
                        try {
                            windowManager?.updateViewLayout(popupView, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(popupView, params)
        handler.postDelayed(closeRunnable, 60_000L)
    }

    private fun removePopup() {
        handler.removeCallbacks(closeRunnable)
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
            popupView = null
        }
    }

    override fun onDestroy() {
        removePopup()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}