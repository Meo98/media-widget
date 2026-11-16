package com.meo.mediawidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService

/**
 * Required for MediaSessionManager.getActiveSessions() permission AND
 * acts as the lifecycle host for MediaSessionTracker — the tracker
 * starts when Android binds us (permission granted, post-boot, etc.)
 * and stops on unbind.
 */
class NotifListener : NotificationListenerService() {

    /** Hell/Dunkel-Wechsel: die Stilfarben werden beim Zeichnen gewählt, also neu zeichnen. */
    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = MediaWidgetProvider.requestUpdate(ctx)
    }
    private var receiverRegistered = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaSessionTracker.start(this)
        if (!receiverRegistered) {
            registerReceiver(configReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
            receiverRegistered = true
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaSessionTracker.stop()
        if (receiverRegistered) {
            unregisterReceiver(configReceiver)
            receiverRegistered = false
        }
    }
}
