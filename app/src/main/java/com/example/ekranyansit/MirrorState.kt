package com.example.ekranyansit

import android.view.Surface

/** Araba tarafındaki Surface'i servis ile paylaşır (aynı process). */
object MirrorState {
    val lock = Any()
    var carSurface: Surface? = null
    @Volatile var running = false
}
