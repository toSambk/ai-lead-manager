package dev.aileadmanager.app

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ThreadLocalRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["TELEGRAM_BOT_TOKEN=123456:integration-test", "server.servlet.session.cookie.secure=false"],
)
@Import(PostgresTestConfiguration::class)
class TelegramAuthenticationIntegrationTests {
    @Value("\${local.server.port}") private lateinit var port: String
    @Autowired private lateinit var jdbc: JdbcTemplate
    private val mapper = ObjectMapper()

    @Test
    fun `telegram login creates a session that can create a lead`() {
        val telegramId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
        val categoryId = jdbc.queryForObject(
            "SELECT id FROM \"AI_LEAD_MANAGER\".service_categories WHERE code = 'automation'",
            Long::class.java,
        )!!
        val client = HttpClient.newBuilder()
            .cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build()
        var leadId: Long? = null
        var inactiveCategoryId: Long? = null
        try {
            val initialCsrf = getCsrf(client, expectSessionCookie = true)
            assertEquals(401, send(client, "GET", "/api/categories").statusCode())
            val login = send(client, "POST", "/api/auth/telegram", """{"initData":"${signedData(telegramId)}"}""", initialCsrf)
            assertEquals(200, login.statusCode(), login.body())
            assertEquals("CUSTOMER", mapper.readTree(login.body()).path("role").stringValue())

            val me = send(client, "GET", "/api/me")
            assertEquals(200, me.statusCode(), me.body())

            inactiveCategoryId = jdbc.queryForObject(
                "INSERT INTO \"AI_LEAD_MANAGER\".service_categories (code, name, active) VALUES (?, 'Hidden category', false) RETURNING id",
                Long::class.java,
                "hidden_${telegramId}",
            )!!
            val categories = send(client, "GET", "/api/categories")
            assertEquals(200, categories.statusCode(), categories.body())
            val items = mapper.readTree(categories.body())
            assertTrue(items.isArray)
            assertTrue(items.any { it.path("id").longValue() == categoryId })
            assertTrue(items.none { it.path("id").longValue() == inactiveCategoryId })

            val csrf = getCsrf(client)
            val created = send(client, "POST", "/api/leads", """
                {"categoryId":$categoryId,"description":"Automate bookings","contactDetails":"@owner"}
            """.trimIndent(), csrf)
            assertEquals(201, created.statusCode(), created.body())
            leadId = mapper.readTree(created.body()).path("id").longValue()
            assertTrue(leadId > 0)
            assertEquals("NEW", mapper.readTree(created.body()).path("status").stringValue())

            jdbc.update(
                "UPDATE \"AI_LEAD_MANAGER\".users SET role = 'MANAGER' WHERE telegram_user_id = ?",
                telegramId,
            )
            val updatedMe = send(client, "GET", "/api/me")
            assertEquals("MANAGER", mapper.readTree(updatedMe.body()).path("role").stringValue())
            val managerAdminProbe = send(client, "GET", "/api/admin/managers")
            assertEquals(403, managerAdminProbe.statusCode(), managerAdminProbe.body())
            val forbidden = send(client, "POST", "/api/leads", """
                {"categoryId":$categoryId,"description":"Another lead","contactDetails":"@owner"}
            """.trimIndent(), csrf)
            assertEquals(403, forbidden.statusCode(), forbidden.body())

            val logout = send(client, "POST", "/api/auth/logout", "{}", csrf)
            assertEquals(204, logout.statusCode(), logout.body())
            assertEquals(401, send(client, "GET", "/api/me").statusCode())
        } finally {
            leadId?.let { jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".leads WHERE id = ?", it) }
            inactiveCategoryId?.let { jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".service_categories WHERE id = ?", it) }
            jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".users WHERE telegram_user_id = ?", telegramId)
        }
    }

    private fun getCsrf(client: HttpClient, expectSessionCookie: Boolean = false): String {
        val response = send(client, "GET", "/api/auth/csrf")
        assertEquals(200, response.statusCode(), response.body())
        val cookieHeaders = response.headers().allValues("set-cookie")
        if (expectSessionCookie) assertTrue(cookieHeaders.isNotEmpty())
        if (cookieHeaders.isNotEmpty()) {
            assertTrue(cookieHeaders.any { it.contains("HttpOnly", ignoreCase = true) })
            assertTrue(cookieHeaders.any { it.contains("SameSite=Lax", ignoreCase = true) })
        }
        return mapper.readTree(response.body()).path("token").stringValue()
    }

    private fun send(client: HttpClient, method: String, path: String, body: String = "", csrf: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .apply { if (csrf != null) header("X-CSRF-TOKEN", csrf) }
            .method(method, if (method == "GET") HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun signedData(telegramId: Long): String {
        val fields = mapOf(
            "auth_date" to Instant.now().epochSecond.toString(),
            "user" to """{"id":$telegramId,"first_name":"Integration"}""",
        )
        val checkString = fields.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        val secret = hmac("WebAppData".toByteArray(StandardCharsets.UTF_8), "123456:integration-test")
        val hash = HexFormat.of().formatHex(hmac(secret, checkString))
        return (fields + ("hash" to hash)).entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
    }

    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }
}
