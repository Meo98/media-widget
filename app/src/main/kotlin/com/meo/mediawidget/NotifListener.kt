package com.meo.mediawidget

import android.service.notification.NotificationListenerService

/**
 * Leere Listener-Implementierung. Existiert ausschließlich, damit
 * MediaSessionManager.getActiveSessions() uns als legitimen
 * Notification-Listener akzeptiert. Verarbeitet selbst keine
 * Notifications.
 */
class NotifListener : NotificationListenerService()
