package com.blacksamdev.bbsmoove

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Chaquopy exige un démarrage explicite avant le premier appel à
 * Python.getInstance() -- sans ça, RoadLookupRepository et
 * DangerZoneRepository planteraient au premier lookup.
 */
class BbsMooveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}
