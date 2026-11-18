package com.example.ekranyansit.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import com.example.ekranyansit.MirrorService
import com.example.ekranyansit.MirrorState

class MirrorCarAppService : CarAppService() {
    // Yan yükleme (sideload) kullanımı için tüm hostlara izin ver.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = MirrorSession()
}

class MirrorSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = MirrorScreen(carContext)
}

class MirrorScreen(ctx: CarContext) : Screen(ctx) {

    init {
        val nav = carContext.getCarService(NavigationManager::class.java)
        nav.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {}
            override fun onAutoDriveEnabled() {}
        })
        nav.navigationStarted()

        carContext.getCarService(androidx.car.app.AppManager::class.java)
            .setSurfaceCallback(object : SurfaceCallback {
                override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
                    synchronized(MirrorState.lock) { MirrorState.carSurface = surfaceContainer.surface }
                }

                override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
                    synchronized(MirrorState.lock) { MirrorState.carSurface = null }
                }
            })
    }

    override fun onGetTemplate(): Template {
        val stop = Action.Builder()
            .setTitle("Durdur")
            .setOnClickListener {
                carContext.startService(
                    Intent(carContext, MirrorService::class.java).setAction(MirrorService.ACTION_STOP)
                )
            }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(stop).build())
            .build()
    }
}
