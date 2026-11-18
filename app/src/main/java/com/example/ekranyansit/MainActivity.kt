package com.example.ekranyansit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var button: Button

    private val capture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (data != null) {
            startForegroundService(
                Intent(this, MirrorService::class.java)
                    .putExtra(MirrorService.EXTRA_CODE, result.resultCode)
                    .putExtra(MirrorService.EXTRA_DATA, data)
            )
            button.postDelayed({ refresh() }, 500)
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* izin verilmese de çalışır, sadece bildirim görünmez */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        val info = TextView(this).apply {
            text = "1) Telefonu arabaya bağla\n2) Başlat'a bas\n3) Android Auto'da 'Ekran Yansıt' uygulamasını aç"
            textSize = 16f
            setPadding(0, 0, 0, 48)
        }
        button = Button(this).apply { setOnClickListener { toggle() } }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(info)
            addView(button)
        })
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun toggle() {
        if (MirrorState.running) {
            startService(Intent(this, MirrorService::class.java).setAction(MirrorService.ACTION_STOP))
            button.postDelayed({ refresh() }, 300)
        } else {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            capture.launch(mpm.createScreenCaptureIntent())
        }
    }

    private fun refresh() {
        button.text = if (MirrorState.running) "Yansıtmayı Durdur" else "Yansıtmayı Başlat"
    }
}
