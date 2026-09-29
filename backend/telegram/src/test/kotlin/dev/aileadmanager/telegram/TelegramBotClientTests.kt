package dev.aileadmanager.telegram

import com.sun.net.httpserver.HttpServer
import dev.aileadmanager.core.TelegramDeliveryErrorCode
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import tools.jackson.databind.ObjectMapper

class TelegramBotClientTests {
    private val mapper = ObjectMapper()

    @Test
    fun `sends text and Mini App button`() = withServer(
        responseStatus = 200,
        responseBody = """{"ok":true,"result":{"message_id":77}}""",
    ) { baseUrl, requestBody ->
        val sent = HttpTelegramBotClient("unit-token", baseUrl).sendMessage(TelegramOutboundMessage(
            chatId = 123,
            text = "Welcome",
            buttonText = "Submit a request",
            buttonUrl = "https://example.com",
        ))

        assertEquals(77, sent.messageId)
        val json = mapper.readTree(requestBody.get())
        assertEquals(123, json.path("chat_id").longValue())
        assertEquals("Welcome", json.path("text").stringValue())
        assertEquals(
            "https://example.com",
            json.path("reply_markup").path("inline_keyboard")[0][0].path("web_app").path("url").stringValue(),
        )
    }

    @Test
    fun `classifies rate limit as retryable and honors retry after`() = withServer(
        responseStatus = 429,
        responseBody = """{"ok":false,"error_code":429,"parameters":{"retry_after":12}}""",
    ) { baseUrl, _ ->
        val error = assertFailsWith<TelegramBotApiException> {
            HttpTelegramBotClient("unit-token", baseUrl).sendMessage(TelegramOutboundMessage(123, "Hello"))
        }

        assertEquals(TelegramDeliveryErrorCode.RATE_LIMITED, error.errorCode)
        assertTrue(error.retryable)
        assertEquals(Duration.ofSeconds(12), error.retryAfter)
    }

    @Test
    fun `classifies forbidden chat as permanent`() = withServer(
        responseStatus = 403,
        responseBody = """{"ok":false,"error_code":403,"description":"Forbidden"}""",
    ) { baseUrl, _ ->
        val error = assertFailsWith<TelegramBotApiException> {
            HttpTelegramBotClient("unit-token", baseUrl).sendMessage(TelegramOutboundMessage(123, "Hello"))
        }

        assertEquals(TelegramDeliveryErrorCode.CHAT_UNAVAILABLE, error.errorCode)
        assertTrue(!error.retryable)
    }

    private fun withServer(
        responseStatus: Int,
        responseBody: String,
        block: (String, AtomicReference<String>) -> Unit,
    ) {
        val requestBody = AtomicReference("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/botunit-token/sendMessage") { exchange ->
            requestBody.set(exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8))
            val bytes = responseBody.toByteArray(StandardCharsets.UTF_8)
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", requestBody)
        } finally {
            server.stop(0)
        }
    }
}
