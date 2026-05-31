package com.splitpay.data.network

import com.splitpay.SplitPayApp
import com.splitpay.data.local.AuthEvents
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    //private const val BASE_URL = "http://10.0.2.2:3000/"   // émulateur Android
    private const val BASE_URL = "http://192.168.0.111:3000/" // appareil physique (WiFi)

    private val authInterceptor = Interceptor { chain ->
        val token = SplitPayApp.instance.tokenManager.accessToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else chain.request()
        chain.proceed(request)
    }

    // Auto-refresh on 401
    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
            // Skip all /auth/* endpoints — a 401 there means bad credentials, not session expiry.
            // Handling it here would incorrectly call notifyExpired() and hide the error message.
            if (response.request.url.encodedPath.startsWith("/auth/")) return null

            val tokenManager = SplitPayApp.instance.tokenManager
            val refreshToken = tokenManager.refreshToken
            if (refreshToken == null) {
                // Pas de refresh token — session définitivement expirée
                tokenManager.clear()
                AuthEvents.notifyExpired()
                return null
            }

            // Synchronous refresh call
            val refreshResponse = try {
                val refreshClient = OkHttpClient()
                val body = """{"refreshToken":"$refreshToken"}"""
                    .toByteArray().let {
                        okhttp3.RequestBody.create("application/json".toMediaType(), it)
                    }
                val req = Request.Builder()
                    .url("${BASE_URL}auth/refresh")
                    .post(body)
                    .build()
                refreshClient.newCall(req).execute()
            } catch (e: Exception) {
                // Pas de réseau — ne pas déconnecter, laisser l'utilisateur réessayer
                return null
            }

            if (!refreshResponse.isSuccessful) {
                tokenManager.clear()
                AuthEvents.notifyExpired()   // redirect to login
                return null
            }

            val json = refreshResponse.body?.string() ?: return null
            val gson = com.google.gson.Gson()
            val auth = gson.fromJson(json, AuthResponse::class.java)

            tokenManager.save(auth.accessToken, auth.refreshToken, auth.userId, auth.name, auth.email)

            return response.request.newBuilder()
                .header("Authorization", "Bearer ${auth.accessToken}")
                .build()
        }
    }

    // Detect 403 suspension — clear token and notify app to show dialog + logout
    private val suspensionInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.code == 403) {
            val bodyStr = response.peekBody(Long.MAX_VALUE).string()
            val msg = runCatching {
                org.json.JSONObject(bodyStr).getString("message")
            }.getOrElse { "Your account has been suspended." }
            if (msg.contains("suspended", ignoreCase = true)) {
                SplitPayApp.instance.tokenManager.clear()
                AuthEvents.notifySuspended(msg)
            }
        }
        response
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(suspensionInterceptor)
        .addInterceptor(logging)
        .authenticator(tokenAuthenticator)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val baseUrl: String get() = BASE_URL
    val httpClient: OkHttpClient get() = client

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
