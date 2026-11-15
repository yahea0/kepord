package com.kepord.ime.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kepord.ime.KepordApplication

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val app = context.applicationContext as? KepordApplication
            app?.clipboardStore?.reloadAsync()
        }
    }
}
