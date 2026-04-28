package app.routemate.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM pushes (booking events, chat messages, ride start).
 * Wiring to RouteMatesApi.registerFcm and notification rendering lands in v1.1.
 */
class RouteMatesMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { /* TODO: register via RouteMatesApi */ }
    override fun onMessageReceived(message: RemoteMessage) { /* TODO: surface notification */ }
}
