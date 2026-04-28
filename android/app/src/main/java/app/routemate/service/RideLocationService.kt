package app.routemate.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.routemate.BuildConfig
import app.routemate.R
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject

/**
 * Foreground service that runs while the driver's ride is `started`.
 *
 * Subscribes to FusedLocationProvider at 5s/20m granularity and forwards each
 * fix as a `{type:"location", lat, lng, ts}` frame on the per-ride WebSocket.
 * The Hilt-injected OkHttpClient already has the JWT auth interceptor; we
 * reuse it to keep one TCP connection per ride.
 */
@AndroidEntryPoint
class RideLocationService : Service() {

    @Inject lateinit var http: OkHttpClient

    private lateinit var fused: com.google.android.gms.location.FusedLocationProviderClient
    private var socket: WebSocket? = null
    private var rideId: String? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val ws = socket ?: return
            val ts = System.currentTimeMillis() / 1000
            ws.send("""{"type":"location","lat":${loc.latitude},"lng":${loc.longitude},"ts":$ts}""")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundCompat()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_RIDE_ID)
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        if (id.isNullOrBlank() || token.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (id == rideId && socket != null) return START_STICKY

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            stopSelf()
            return START_NOT_STICKY
        }

        rideId = id
        connectSocket(id, token)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(20f)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())

        return START_STICKY
    }

    private fun connectSocket(id: String, token: String) {
        val wsBase = BuildConfig.API_BASE_URL.removeSuffix("/")
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        val url = "$wsBase/v1/ws/ride/$id?token=$token"
        socket = http.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {},
        )
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        socket?.cancel()
        socket = null
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live ride", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun startForegroundCompat() {
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.ride_active))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ride_active"
        private const val NOTIF_ID = 1001
        private const val EXTRA_RIDE_ID = "ride_id"
        private const val EXTRA_TOKEN = "token"

        fun start(ctx: Context, rideId: String, token: String) {
            val intent = Intent(ctx, RideLocationService::class.java).apply {
                putExtra(EXTRA_RIDE_ID, rideId)
                putExtra(EXTRA_TOKEN, token)
            }
            ContextCompat.startForegroundService(ctx, intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, RideLocationService::class.java))
        }
    }
}
