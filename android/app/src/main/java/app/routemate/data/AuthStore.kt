package app.routemate.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth")
private val TOKEN = stringPreferencesKey("app_jwt")

@Singleton
class AuthStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    val tokenFlow: Flow<String?> = ctx.dataStore.data.map { it[TOKEN] }

    suspend fun token(): String? = ctx.dataStore.data.map { it[TOKEN] }.first()

    suspend fun setToken(value: String?) {
        ctx.dataStore.edit { p ->
            if (value == null) p.remove(TOKEN) else p[TOKEN] = value
        }
    }
}
