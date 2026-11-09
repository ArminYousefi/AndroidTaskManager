package com.google.mytaskmanager.core

import com.google.mytaskmanager.data.remote.dto.ApiErrorBody
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

private val errorAdapter by lazy { Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(ApiErrorBody::class.java) }

/**
 * Converts *any* throwable into an [AppException].
 *
 * - [HttpException]: the server's uniform error body (`{"error":{code,message,details,requestId}}`) is parsed so the
 *   real reason reaches the user; unparsable bodies fall back to the HTTP status.
 * - Timeouts and other I/O failures become [ErrorKind.TIMEOUT] / [ErrorKind.NETWORK].
 * - Bad JSON becomes [ErrorKind.UNKNOWN] with code `BAD_RESPONSE`.
 */
fun Throwable.toAppException(): AppException = when (this) {
    is AppException -> this
    is HttpException -> {
        val parsed = runCatching { response()?.errorBody()?.string()?.let { errorAdapter.fromJson(it) }?.error }.getOrNull()
        val kind = ErrorKind.fromHttp(code())
        AppException(
            kind = kind,
            code = parsed?.code ?: "HTTP_${code()}",
            userMessage = ErrorMessages.userMessage(kind, parsed?.message),
            details = parsed?.details ?: emptyMap(),
            requestId = parsed?.requestId ?: response()?.headers()?.get("X-Request-Id"),
            cause = this
        )
    }
    is SocketTimeoutException -> AppException(ErrorKind.TIMEOUT, "TIMEOUT", ErrorMessages.userMessage(ErrorKind.TIMEOUT, null), cause = this)
    is IOException -> AppException(ErrorKind.NETWORK, "NETWORK", ErrorMessages.userMessage(ErrorKind.NETWORK, null), cause = this)
    is JsonDataException, is JsonEncodingException ->
        AppException(ErrorKind.UNKNOWN, "BAD_RESPONSE", ErrorMessages.userMessage(ErrorKind.UNKNOWN, null), cause = this)
    else -> AppException(ErrorKind.UNKNOWN, "UNEXPECTED", ErrorMessages.userMessage(ErrorKind.UNKNOWN, null), cause = this)
}

/**
 * Runs [block] and rethrows every failure as an [AppException]. Coroutine cancellation is passed through untouched.
 * Wrap each network call of a repository in this.
 */
suspend inline fun <T> appCall(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    throw e.toAppException()
}
