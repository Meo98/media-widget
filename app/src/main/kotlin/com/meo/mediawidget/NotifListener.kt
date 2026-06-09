package com.meo.mediawidget

import android.service.notification.NotificationListenerService

/**
 * Required for MediaSessionManager.getActiveSessions() permission AND
 * acts as the lifecycle host for MediaSessionTracker — the tracker
 * starts when Android binds us (permission granted, post-boot, etc.)
 * and stops on unbind.
 */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaSessionTracker.start(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaSessionTracker.stop()
    }
}
