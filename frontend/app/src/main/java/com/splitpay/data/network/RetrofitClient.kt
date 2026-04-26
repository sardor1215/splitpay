package com.splitpay.data.network

import com.splitpay.SplitPayApp
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

    private const val BASE_URL = "http://10.0.2.2:8080/"

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
            // Avoid infinite loop if refresh itself fails
            if (response.request.url.toString().contains("/auth/refresh")) return null

            val tokenManager = SplitPayApp.instance.tokenManager
            val refreshToken = tokenManager.refreshToken ?: return null

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
            } catch (e: Exception) { return null }

            if (!refreshResponse.isSuccessful) {
                tokenManager.clear()
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

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .authenticator(tokenAuthenticator)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
