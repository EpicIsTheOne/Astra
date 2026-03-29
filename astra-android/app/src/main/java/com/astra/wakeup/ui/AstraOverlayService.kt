package com.astra.wakeup.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.Rect
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.astra.wakeup.R

class AstraOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var orbView: View? = null
    private var orbParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var panelController: AstraOverlayPanelController? = null
    private var callCompactView: View? = null
    private var callCompactParams: WindowManager.LayoutParams? = null
    private var tvCallCompactPhase: TextView? = null
    private var tvCallCompactTimer: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var unsubscribeCallState: (() -> Unit)? = null
    private var lastOutsideTapAtMs: Long = 0L
    private var lastCallCompactTapAtMs: Long = 0L
    private var currentCallState: CallState = CallState()
    private val callTimerTicker = object : Runnable {
        override fun run() {
            updateCompactCallUi(currentCallState)
            if (currentCallState.active) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureChannel()
        unsubscribeCallState = CallStateRepository.subscribe { state ->
            handler.post { handleCallStateChanged(state) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AstraOverlayController.isOverlayEnabled(this) && intent?.action != ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_EXPAND -> {
                startOverlayForeground()
                if (currentCallState.active) {
                    showCompactCallUiIfNeeded()
                } else {
                    showPanelIfNeeded()
                }
                return START_STICKY
            }
            else -> {
                startOverlayForeground()
                if (currentCallState.active) {
                    showCompactCallUiIfNeeded()
                } else {
                    showOrbIfNeeded()
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        unsubscribeCallState?.invoke()
        unsubscribeCallState = null
        handler.removeCallbacks(callTimerTicker)
        removePanel()
        removeCompactCallUi()
        removeOrb()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startOverlayForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val expandPending = PendingIntent.getService(
            this,
            7103,
            Intent(this, AstraOverlayService::class.java).apply { action = ACTION_EXPAND },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
            .setContentIntent(expandPending)
            .addAction(0, "Open panel", expandPending)
            .addAction(0, "Stop overlay", stopPending)
            .build()
    }

    private fun showOrbIfNeeded() {
        if (currentCallState.active) return
        if (orbView != null || !AstraOverlayController.isOverlayEnabled(this) || !AstraOverlayController.canDrawOverlays(this)) return
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
        val size = (AstraOverlayController.orbSizeDp(this) * resources.displayMetrics.density).toInt()
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
                        showPanelIfNeeded()
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

    private fun showPanelIfNeeded() {
        if (currentCallState.active) {
            showCompactCallUiIfNeeded()
            return
        }
        if (panelView != null || !AstraOverlayController.isOverlayEnabled(this) || !AstraOverlayController.canDrawOverlays(this)) return
        removeOrb()
        removeCompactCallUi()
        lastOutsideTapAtMs = 0L
        val panel = LayoutInflater.from(this).inflate(R.layout.activity_astra_overlay, null)
        val panelCard = panel.findViewById<View>(R.id.overlayPanelCard)
        panel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isTouchOutsidePanelCard(panelCard, event)) {
                        handleOutsideTap()
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_OUTSIDE -> {
                    handleOutsideTap()
                    true
                }
                else -> false
            }
        }
        panelController = AstraOverlayPanelController(
            context = this,
            root = panel,
            requestMicPermission = {
                startActivity(AstraPanelLauncher.intent(this))
            },
            onCloseRequested = {
                collapseToOrb()
            },
            onCallRequested = {
                startActivity(Intent(this, ChatActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(ChatActivity.EXTRA_AUTO_START_CALL, true)
                })
            }
        )
        panelController?.onShow()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
        }
        runCatching { windowManager.addView(panel, params) }
        panelView = panel
        panelParams = params
    }

    private fun collapseToOrb() {
        val panel = panelView
        if (panel == null) {
            removePanel()
            showOrbIfNeeded()
            return
        }
        panel.animate()
            .alpha(0f)
            .setDuration(180)
            .withEndAction {
                removePanel()
                showOrbIfNeeded()
            }
            .start()
    }

    private fun handleOutsideTap() {
        val now = System.currentTimeMillis()
        if (now - lastOutsideTapAtMs <= OUTSIDE_DOUBLE_TAP_WINDOW_MS) {
            lastOutsideTapAtMs = 0L
            collapseToOrb()
        } else {
            lastOutsideTapAtMs = now
        }
    }

    private fun isTouchOutsidePanelCard(panelCard: View, event: MotionEvent): Boolean {
        val rect = Rect()
        panelCard.getGlobalVisibleRect(rect)
        return !rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }

    private fun handleCallStateChanged(state: CallState) {
        currentCallState = state
        if (state.active) {
            removePanel()
            removeOrb()
            showCompactCallUiIfNeeded()
            updateCompactCallUi(state)
            handler.removeCallbacks(callTimerTicker)
            handler.post(callTimerTicker)
        } else {
            handler.removeCallbacks(callTimerTicker)
            removeCompactCallUi()
            if (AstraOverlayController.isOverlayEnabled(this) && AstraOverlayController.canDrawOverlays(this)) {
                showOrbIfNeeded()
            }
        }
    }

    private fun showCompactCallUiIfNeeded() {
        if (!currentCallState.active) return
        if (callCompactView != null || !AstraOverlayController.isOverlayEnabled(this) || !AstraOverlayController.canDrawOverlays(this)) return
        val view = LayoutInflater.from(this).inflate(R.layout.view_overlay_call_compact, null)
        tvCallCompactPhase = view.findViewById(R.id.tvOverlayCallPhase)
        tvCallCompactTimer = view.findViewById(R.id.tvOverlayCallTimer)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 220
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    val now = System.currentTimeMillis()
                    if (now - lastCallCompactTapAtMs <= COMPACT_CALL_DOUBLE_TAP_WINDOW_MS) {
                        lastCallCompactTapAtMs = 0L
                        sendBroadcast(Intent(ChatActivity.ACTION_END_CALL))
                    } else {
                        lastCallCompactTapAtMs = now
                    }
                    true
                }
                else -> true
            }
        }
        runCatching { windowManager.addView(view, params) }
        callCompactView = view
        callCompactParams = params
    }

    private fun updateCompactCallUi(state: CallState) {
        tvCallCompactPhase?.text = when {
            state.phase.isBlank() -> "Call live"
            state.phase.startsWith("Call:") -> state.phase.removePrefix("Call:").trim().replaceFirstChar { it.uppercase() }
            else -> state.phase.replaceFirstChar { it.uppercase() }
        }
        val startedAt = state.callStartedAtMs
        tvCallCompactTimer?.text = if (startedAt == null) {
            "00:00"
        } else {
            formatElapsed(System.currentTimeMillis() - startedAt)
        }
    }

    private fun formatElapsed(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun removePanel() {
        panelController?.release()
        panelController = null
        panelView?.let { view -> runCatching { windowManager.removeView(view) } }
        panelView = null
        panelParams = null
    }

    private fun removeCompactCallUi() {
        tvCallCompactPhase = null
        tvCallCompactTimer = null
        callCompactView?.let { view -> runCatching { windowManager.removeView(view) } }
        callCompactView = null
        callCompactParams = null
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
        const val ACTION_EXPAND = "com.astra.wakeup.action.EXPAND_ASTRA_OVERLAY"
        private const val CHANNEL_ID = "astra_overlay"
        private const val NOTIFICATION_ID = 7110
        private const val OUTSIDE_DOUBLE_TAP_WINDOW_MS = 450L
        private const val COMPACT_CALL_DOUBLE_TAP_WINDOW_MS = 450L
    }
}
