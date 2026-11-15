package com.kepord.ime

import android.app.Application
import com.kepord.ime.clipboard.ClipboardStore
import com.kepord.ime.data.KepordPreferences

class KepordApplication : Application() {

    lateinit var preferences: KepordPreferences
        private set

    lateinit var clipboardStore: ClipboardStore
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = KepordPreferences(this)
        clipboardStore = ClipboardStore(this, preferences)
        clipboardStore.reloadAsync()
    }
}
