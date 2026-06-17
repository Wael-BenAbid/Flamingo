package com.example.flamingoandroid

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

class FlamingoApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
