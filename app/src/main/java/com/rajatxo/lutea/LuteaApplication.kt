package com.rajatxo.lutea

import android.app.Application
import com.rajatxo.lutea.data.store.PlaylistStore

class LuteaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PlaylistStore.init(this)
    }
}
