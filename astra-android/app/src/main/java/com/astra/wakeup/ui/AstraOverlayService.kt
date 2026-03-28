package com.astra.wakeup.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.astra.wakeup.R

class AstraOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var orbView: View? = null
    private var orbParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                showOrbIfNeeded()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        removeOrb()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val openPending = AstraPanelLauncher.pendingIntent(this, 7103)
        val stopPending = PendingIntent.getService(
            this,
            7104,
            Intent(this, AstraOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Astra overlay")
            .setContentText("Floating orb ready. Tap to summon Astra from anywhere.")
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "Open panel", openPending)
            .addAction(0, "Stop overlay", stopPending)
            .build()
    }

    private fun showOrbIfNeeded() {
        if (orbView != null || !AstraOverlayController.canDrawOverlays(this)) return
        val orb = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_astra_orb)
            elevation = 18f
            alpha = 0.94f
            addView(TextView(context).apply {
                text = "A"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val size = (64 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 220
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        orb.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (initialX - (event.rawX - touchX)).toInt()
                    params.y = (initialY + (event.rawY - touchY)).toInt()
                    runCatching { windowManager.updateViewLayout(orb, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - touchX) > 10 || kotlin.math.abs(event.rawY - touchY) > 10
                    if (!moved) {
                        startActivity(AstraPanelLauncher.intent(this))
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { windowManager.addView(orb, params) }
        orbView = orb
        orbParams = params
    }

    private fun removeOrb() {
        orbView?.let { view -> runCatching { windowManager.removeView(view) } }
        orbView = null
        orbParams = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Astra overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_STOP = "com.astra.wakeup.action.STOP_ASTRA_OVERLAY"
        private const val CHANNEL_ID = "astra_overlay"
        private const val NOTIFICATION_ID = 7110
    }
}
