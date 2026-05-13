package app.routemate.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import app.routemate.MainActivity
import app.routemate.R
import app.routemate.data.AuthStore
import app.routemate.data.FcmRegister
import app.routemate.data.RouteMatesApi
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives FCM data + notification messages from the backend, shows a
 * channelled notification, and deeplinks back into RideDetail when the
 * payload carries a `ride_id`. New device tokens are forwarded to the
 * backend so /v1/devices/fcm always reflects the live token.
 */
@AndroidEntryPoint
class RouteMatesMessagingService : FirebaseMessagingService() {

    @Inject lateinit var api: RouteMatesApi
    @Inject lateinit var authStore: AuthStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            // Only register once we have an app JWT; otherwise the API call
            // would 401 and the token will be picked up post-sign-in by the
            // explicit refresh in SignInViewModel.
            if (authStore.cachedToken().isNullOrBlank()) return@launch
            runCatching { api.registerFcm(FcmRegister(token = token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ensureChannel()

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""
        val rideId = message.data["ride_id"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (rideId != null) data = Uri.parse("routemate://ride/$rideId")
        }
        val pending = PendingIntent.getActivity(
            this,
            rideId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((rideId ?: message.messageId ?: "0").hashCode(), notif)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Ride events",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "Bookings, ride start, cancellations and chat" }
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "ride_events"
    }
}
