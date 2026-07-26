package com.erdman.erdstream.data

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Adds Subsonic API authentication and common query parameters (u, t, s, v,
 * c, f) to every request. Uses token-based auth (token = md5(password +
 * salt), with a fresh random salt per request) so the plaintext password is
 * never sent over the network.
 */
class SubsonicAuthInterceptor(
    private val credentialsManager: CredentialsManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = credentialsManager.credentials.value
            ?: return chain.proceed(chain.request())

        val salt = randomSalt()
        val token = md5Hex(credentials.password + salt)

        val originalRequest = chain.request()
        val newUrl = originalRequest.url.newBuilder()
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)
            .addQueryParameter("f", "json")
            .build()

        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()

        return chain.proceed(newRequest)
    }

    private fun randomSalt(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "ErdStream"
    }
}
