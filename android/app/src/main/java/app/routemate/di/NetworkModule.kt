package app.routemate.di

import app.routemate.BuildConfig
import app.routemate.data.AuthStore
import app.routemate.data.RouteMatesApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides @Singleton
    fun okHttp(authStore: AuthStore): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = authStore.cachedToken()
                val req = if (token.isNullOrBlank()) chain.request()
                          else chain.request().newBuilder()
                              .header("Authorization", "Bearer $token")
                              .build()
                chain.proceed(req)
            }
            .addInterceptor(log)
            .build()
    }

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" })
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun api(retrofit: Retrofit): RouteMatesApi = retrofit.create(RouteMatesApi::class.java)
}
