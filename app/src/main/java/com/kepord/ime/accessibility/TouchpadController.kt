package com.kepord.ime.accessibility

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object TouchpadController {

    interface ServiceBridge {
        fun showFloatingTouchpad()
        fun hideFloatingTouchpad()
        fun isShowing(): Boolean
    }

    interface ImeBridge {
        fun onReturnToNormalKeyboard()
    }

    @Volatile
    var serviceBridge: ServiceBridge? = null

    @Volatile
    var imeBridge: ImeBridge? = null

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!enabled) return false

        val expected = "${context.packageName}/${KepordAccessibilityService::class.java.name}"
        val expectedShort = "${context.packageName}/.accessibility.KepordAccessibilityService"
        val settingVal = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(settingVal)
        while (splitter.hasNext()) {
            val s = splitter.next()
            if (s.equals(expected, ignoreCase = true) || s.equals(expectedShort, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun requestOpenTouchpad(context: Context): Boolean {
        val bridge = serviceBridge ?: return false
        if (!isAccessibilityEnabled(context)) return false
        bridge.showFloatingTouchpad()
        return true
    }

    fun closeTouchpadAndReturnToKeyboard() {
        serviceBridge?.hideFloatingTouchpad()
        imeBridge?.onReturnToNormalKeyboard()
    }
}
