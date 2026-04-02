package com.splitpay.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    // Separate bare client (no interceptor) used only for token refresh — avoids infinite loop
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
        val response = chain.proceed(original.withAccessToken())

        // Not a 401 or this is already a refresh/auth request — return as-is
        if (response.code != 401 || original.url.encodedPath.contains("auth/")) {
            return response
        }

        // Try to refresh the access token
        val refreshToken = tokenManager.getRefreshToken() ?: return response
        return try {
            val refreshResponse = runCatching {
                kotlinx.coroutines.runBlocking {
                    refreshApi.refresh(RefreshRequest(refreshToken))
                }
            }.getOrNull()

            if (refreshResponse?.isSuccessful == true) {
                val body = refreshResponse.body()!!
                tokenManager.saveTokens(body.accessToken, body.refreshToken)
                response.close()
                // Retry the original request with the new token
                chain.proceed(original.withAccessToken())
            } else {
                // Refresh failed — token is truly invalid, clear session
                tokenManager.clear()
                response
            }
        } catch (_: Exception) {
            response
        }
    }

    private fun Request.withAccessToken(): Request {
        val token = tokenManager.getAccessToken() ?: return this
        return newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}
