package com.example.ekranyansit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.view.WindowManager

/**
 * Telefon ekranını yakalar ve arabanın Surface'ine çizer.
 * Çalıştığı sürece ekran kilitlenmez (wake lock). Güç tuşu yine de ekranı kapatır.
 */
class MirrorService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var bitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null || MirrorState.running) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            1, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(code, data)
        projection = proj

        thread = HandlerThread("mirror").also { it.start() }
        val handler = Handler(thread!!.looper)

        // Android 14+: createVirtualDisplay'den önce callback kaydı şart.
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)

        startCapture(proj, handler)
        acquireWakeLock()
        MirrorState.running = true
        return START_NOT_STICKY
    }

    private fun startCapture(proj: MediaProjection, handler: Handler) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val bounds = wm.maximumWindowMetrics.bounds
        val dpi = resources.displayMetrics.densityDpi

        // Performans için genişliği en fazla 720'ye düşür.
        val scale = minOf(1f, 720f / bounds.width())
        val w = (bounds.width() * scale).toInt() and 1.inv()
        val h = (bounds.height() * scale).toInt() and 1.inv()

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val rowPadding = plane.rowStride - plane.pixelStride * w
                val bmpW = w + rowPadding / plane.pixelStride
                var bmp = bitmap
                if (bmp == null || bmp.width != bmpW || bmp.height != h) {
                    bmp = Bitmap.createBitmap(bmpW, h, Bitmap.Config.ARGB_8888)
                    bitmap = bmp
                }
                bmp!!.copyPixelsFromBuffer(plane.buffer)
                drawToCar(bmp, w, h)
            } finally {
                image.close()
            }
        }, handler)

        virtualDisplay = proj.createVirtualDisplay(
            "EkranYansit", w, h, (dpi * scale).toInt(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
    }

    /** Görüntüyü oranını bozmadan arabanın ekranına ortalayarak çizer. */
    private fun drawToCar(bmp: Bitmap, w: Int, h: Int) {
        synchronized(MirrorState.lock) {
            val surface = MirrorState.carSurface ?: return
            if (!surface.isValid) return
            try {
                val canvas = surface.lockHardwareCanvas()
                try {
                    canvas.drawColor(0xFF000000.toInt())
                    val cw = canvas.width.toFloat()
                    val ch = canvas.height.toFloat()
                    val s = minOf(cw / w, ch / h)
                    val dw = w * s
                    val dh = h * s
                    val left = ((cw - dw) / 2).toInt()
                    val top = ((ch - dh) / 2).toInt()
                    canvas.drawBitmap(
                        bmp, Rect(0, 0, w, h),
                        Rect(left, top, left + dw.toInt(), top + dh.toInt()), null
                    )
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
            } catch (_: Exception) {
                // Surface o sırada yok edilmiş olabilir; sonraki kareyi bekle.
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "EkranYansit:mirror"
        ).apply { acquire() }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("mirror", "Ekran yansıtma", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, "mirror")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Ekran yansıtılıyor")
            .setContentText("Durdurmak için dokunun")
            .setContentIntent(stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        MirrorState.running = false
        wakeLock?.let { if (it.isHeld) it.release() }
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        thread?.quitSafely()
        bitmap = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "com.example.ekranyansit.STOP"
    }
}
