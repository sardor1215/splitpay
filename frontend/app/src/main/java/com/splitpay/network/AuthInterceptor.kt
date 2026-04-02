package com.splitpay.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.locks.ReentrantLock

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    // Ensures only ONE refresh call happens at a time.
    // Other threads wait, then reuse the new token instead of triggering
    // another refresh (which would fail because of token rotation).
    private val refreshLock = ReentrantLock()

    // Bare client with no interceptors — used only for the sync refresh call
    private val refreshApi: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(RetrofitClient.BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val response = chain.proceed(original.withBearerToken())

        // Not a 401 or it's an auth endpoint — return as-is
        if (response.code != 401 || original.url.encodedPath.contains("auth/")) {
            return response
        }

        // Acquire lock — only one coroutine refreshes at a time
        refreshLock.lock()
        return try {
            // Another thread may have already refreshed while we waited —
            // check if the token changed before doing another refresh
            val tokenBeforeLock = original.header("Authorization")
            val currentToken = tokenManager.getAccessToken()?.let { "Bearer $it" }

            if (currentToken != null && currentToken != tokenBeforeLock) {
                // Token was refreshed by another thread — just retry with new token
                response.close()
                return chain.proceed(original.withBearerToken())
            }

            // We are the first — do the refresh
            val refreshToken = tokenManager.getRefreshToken()
                ?: return response  // no refresh token, can't recover

            val refreshResponse = try {
                refreshApi.refreshSync(RefreshRequest(refreshToken)).execute()
            } catch (_: Exception) {
                return response
            }

            if (refreshResponse.isSuccessful) {
                val body = refreshResponse.body()!!
                tokenManager.saveTokens(body.accessToken, body.refreshToken)
                response.close()
                chain.proceed(original.withBearerToken())
            } else {
                // Refresh token truly expired/revoked — clear session
                tokenManager.clear()
                response
            }
        } finally {
            refreshLock.unlock()
        }
    }

    private fun Request.withBearerToken(): Request {
        val token = tokenManager.getAccessToken() ?: return this
        return newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}
