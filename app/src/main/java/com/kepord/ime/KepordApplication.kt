package com.kepord.ime

import android.app.Application
import com.kepord.ime.ai.KepordAiEngine
import com.kepord.ime.ai.KepordAiPrefs
import com.kepord.ime.clipboard.ClipboardStore
import com.kepord.ime.data.KepordPreferences
import com.kepord.ime.ime.MechanicalSoundEngine

class KepordApplication : Application() {

    lateinit var preferences: KepordPreferences
        private set

    lateinit var clipboardStore: ClipboardStore
        private set

    lateinit var aiPrefs: KepordAiPrefs
        private set

    lateinit var aiEngine: KepordAiEngine
        private set

    lateinit var soundEngine: MechanicalSoundEngine
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = KepordPreferences(this)
        clipboardStore = ClipboardStore(this, preferences)
        clipboardStore.reloadAsync()
        aiPrefs = KepordAiPrefs(this)
        aiEngine = KepordAiEngine(aiPrefs)
        soundEngine = MechanicalSoundEngine(this)
    }
}
