package app.routemate

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class RouteMatesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        // Crashlytics auto-initialises when Firebase is configured. On builds
        // without google-services.json (dev / CI), FirebaseApp.getInstance()
        // throws and we silently skip — uncaught exceptions still hit logcat.
        runCatching {
            FirebaseApp.getInstance()
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        }
    }
}
