package dev.aileadmanager.telegram

import dev.aileadmanager.core.TelegramDeliveryErrorCode
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

data class TelegramOutboundMessage(
    val chatId: Long,
    val text: String,
    val buttonText: String? = null,
    val buttonUrl: String? = null,
)

data class SentTelegramMessage(val messageId: Long)

interface TelegramBotClient {
    fun sendMessage(message: TelegramOutboundMessage): SentTelegramMessage
}

class TelegramBotApiException(
    val errorCode: TelegramDeliveryErrorCode,
    val retryable: Boolean,
    val retryAfter: Duration? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class HttpTelegramBotClient(
    private val botToken: String,
    private val apiBaseUrl: String = "https://api.telegram.org",
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val requestTimeout: Duration = Duration.ofSeconds(20),
) : TelegramBotClient {
    private val mapper = ObjectMapper()

    override fun sendMessage(message: TelegramOutboundMessage): SentTelegramMessage {
        if (botToken.isBlank()) {
            throw TelegramBotApiException(
                TelegramDeliveryErrorCode.AUTHENTICATION_FAILED,
                retryable = false,
                message = "Telegram bot token is not configured",
            )
        }
        require((message.buttonText == null) == (message.buttonUrl == null)) {
            "Telegram button text and URL must be supplied together"
        }
        val payload = linkedMapOf<String, Any>(
            "chat_id" to message.chatId,
            "text" to message.text,
        )
        if (message.buttonText != null && message.buttonUrl != null) {
            payload["reply_markup"] = mapOf(
                "inline_keyboard" to listOf(listOf(mapOf(
                    "text" to message.buttonText,
                    "web_app" to mapOf("url" to message.buttonUrl),
                ))),
            )
        }
        val request = HttpRequest.newBuilder(methodUri("sendMessage"))
            .timeout(requestTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw networkFailure(error)
        } catch (error: IOException) {
            throw networkFailure(error)
        }
        return parseSendMessageResponse(response.statusCode(), response.body())
    }

    private fun parseSendMessageResponse(status: Int, body: String): SentTelegramMessage {
        val response = try {
            mapper.readTree(body)
        } catch (_: JacksonException) {
            throw classify(status, null, null)
        }
        if (status in 200..299 && response.path("ok").booleanValue()) {
            val messageId = response.path("result").path("message_id")
            if (messageId.isIntegralNumber) return SentTelegramMessage(messageId.longValue())
            throw TelegramBotApiException(
                TelegramDeliveryErrorCode.INVALID_REQUEST,
                retryable = false,
                message = "Telegram Bot API returned an invalid success response",
            )
        }
        val apiCode = response.path("error_code").takeIf { it.isIntegralNumber }?.intValue()
        val retryAfterSeconds = response.path("parameters").path("retry_after")
            .takeIf { it.isIntegralNumber && it.longValue() > 0 }
            ?.longValue()
        throw classify(status, apiCode, retryAfterSeconds)
    }

    private fun classify(status: Int, apiCode: Int?, retryAfterSeconds: Long?): TelegramBotApiException {
        val code = apiCode ?: status
        return when {
            code == 429 -> TelegramBotApiException(
                TelegramDeliveryErrorCode.RATE_LIMITED,
                retryable = true,
                retryAfter = retryAfterSeconds?.let(Duration::ofSeconds),
                message = "Telegram Bot API rate limit was reached",
            )
            code >= 500 || code <= 0 -> TelegramBotApiException(
                TelegramDeliveryErrorCode.PROVIDER_UNAVAILABLE,
                retryable = true,
                message = "Telegram Bot API is unavailable",
            )
            code == 401 -> TelegramBotApiException(
                TelegramDeliveryErrorCode.AUTHENTICATION_FAILED,
                retryable = false,
                message = "Telegram Bot API authentication failed",
            )
            code == 403 -> TelegramBotApiException(
                TelegramDeliveryErrorCode.CHAT_UNAVAILABLE,
                retryable = false,
                message = "Telegram chat is unavailable",
            )
            else -> TelegramBotApiException(
                TelegramDeliveryErrorCode.INVALID_REQUEST,
                retryable = false,
                message = "Telegram Bot API rejected the request",
            )
        }
    }

    private fun networkFailure(cause: Exception) = TelegramBotApiException(
        TelegramDeliveryErrorCode.NETWORK_ERROR,
        retryable = true,
        message = "Telegram Bot API request failed",
        cause = cause,
    )

    private fun methodUri(method: String): URI = URI.create("${apiBaseUrl.trimEnd('/')}/bot$botToken/$method")
}
