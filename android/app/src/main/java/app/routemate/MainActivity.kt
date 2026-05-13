package app.routemate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.routemate.ui.RootNav
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val pendingRideId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRideId.value = rideIdFromIntent(intent)
        setContent {
            RouteMatesTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    RootNav(pendingRideIdFlow = pendingRideId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        rideIdFromIntent(intent)?.let { pendingRideId.value = it }
    }

    /** routemate://ride/{id} — last path segment is the ride id. */
    private fun rideIdFromIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "routemate" || uri.host != "ride") return null
        return uri.lastPathSegment
    }
}

@Composable
private fun RouteMatesTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    // dynamic*ColorScheme is @RequiresApi(31); minSdk is 26 so fall back
    // to a static Material 3 scheme on Android 8–11.
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
