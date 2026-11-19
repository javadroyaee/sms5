package ir.jrweb.banksms

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class LockPopupActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.popup_overlay)

        val type = intent.getStringExtra("TYPE") ?: "DEPOSIT"
        val amount = intent.getStringExtra("AMOUNT") ?: "0"

        val container = findViewById<LinearLayout>(R.id.popupContainer)
        val tvAmount = findViewById<TextView>(R.id.tvPopupAmount)

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

        var startX = 0f
        var startY = 0f

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.translationX = event.rawX - startX
                    v.translationY = event.rawY - startY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - startX)
                    val dy = abs(event.rawY - startY)
                    if (dx > 100 || dy > 100 || (dx < 20 && dy < 20)) {
                        finish()
                    } else {
                        v.animate().translationX(0f).translationY(0f).setDuration(150).start()
                    }
                    true
                }
                else -> false
            }
        }

        handler.postDelayed(finishRunnable, 60_000L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        super.onDestroy()
    }
}