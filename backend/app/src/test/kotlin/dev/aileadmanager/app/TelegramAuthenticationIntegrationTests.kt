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
    properties = [
        "TELEGRAM_BOT_TOKEN=123456:integration-test",
        "server.servlet.session.cookie.secure=false",
        "ai.worker.enabled=false",
    ],
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
            assertEquals(404, send(client, "GET", "/api/dev/auth/users").statusCode())
            assertEquals(401, send(client, "GET", "/api/categories").statusCode())
            val login = send(client, "POST", "/api/auth/telegram", """{"initData":"${signedData(telegramId)}"}""", initialCsrf)
            assertEquals(200, login.statusCode(), login.body())
            val authenticatedUser = mapper.readTree(login.body())
            assertEquals("CUSTOMER", authenticatedUser.path("role").stringValue())
            val authenticatedUserId = authenticatedUser.path("id").longValue()

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
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT count(*) FROM \"AI_LEAD_MANAGER\".ai_jobs WHERE lead_id = ? AND status = 'PENDING'",
                    Int::class.java,
                    leadId,
                ),
            )

            val customerLeadList = send(client, "GET", "/api/leads?page=0&size=20")
            assertEquals(200, customerLeadList.statusCode(), customerLeadList.body())
            val leadItems = mapper.readTree(customerLeadList.body()).path("items")
            assertTrue(leadItems.any { it.path("id").longValue() == leadId })
            assertEquals("Automation", leadItems.first { it.path("id").longValue() == leadId }.path("category").path("name").stringValue())

            val customerLeadDetail = send(client, "GET", "/api/leads/$leadId")
            assertEquals(200, customerLeadDetail.statusCode(), customerLeadDetail.body())
            assertEquals("LM-${leadId.toString().padStart(6, '0')}", mapper.readTree(customerLeadDetail.body()).path("reference").stringValue())
            val initialVersion = mapper.readTree(customerLeadDetail.body()).path("version").longValue()
            assertEquals(404, send(client, "GET", "/api/leads/${Long.MAX_VALUE}").statusCode())
            assertEquals(400, send(client, "GET", "/api/leads?page=-1").statusCode())
            assertEquals(403, send(client, "GET", "/api/leads/$leadId/events").statusCode())
            assertEquals(403, send(client, "GET", "/api/leads/$leadId/notes").statusCode())
            assertEquals(403, send(client, "GET", "/api/leads/$leadId/ai-analysis").statusCode())
            assertEquals(
                403,
                send(client, "POST", "/api/leads/$leadId/notes", """{"body":"Private note"}""", csrf).statusCode(),
            )
            assertEquals(403, send(client, "GET", "/api/managers").statusCode())
            assertEquals(
                403,
                send(client, "PATCH", "/api/leads/$leadId/status", """{"status":"IN_PROGRESS","version":$initialVersion}""", csrf).statusCode(),
            )
            assertEquals(
                403,
                send(client, "PATCH", "/api/leads/$leadId/assignee", """{"ownerId":$authenticatedUserId,"version":$initialVersion}""", csrf).statusCode(),
            )

            jdbc.update(
                "UPDATE \"AI_LEAD_MANAGER\".users SET role = 'MANAGER' WHERE telegram_user_id = ?",
                telegramId,
            )
            val updatedMe = send(client, "GET", "/api/me")
            assertEquals("MANAGER", mapper.readTree(updatedMe.body()).path("role").stringValue())
            val managerAdminProbe = send(client, "GET", "/api/admin/managers")
            assertEquals(403, managerAdminProbe.statusCode(), managerAdminProbe.body())
            val managers = send(client, "GET", "/api/managers")
            assertEquals(200, managers.statusCode(), managers.body())
            assertTrue(mapper.readTree(managers.body()).any { it.path("id").longValue() == authenticatedUserId })

            val pendingAnalysis = send(client, "GET", "/api/leads/$leadId/ai-analysis")
            assertEquals(200, pendingAnalysis.statusCode(), pendingAnalysis.body())
            assertEquals("PENDING", mapper.readTree(pendingAnalysis.body()).path("status").stringValue())
            assertEquals(
                409,
                send(client, "POST", "/api/leads/$leadId/ai-analysis/retry", "{}", csrf).statusCode(),
            )
            val failedJobId = mapper.readTree(pendingAnalysis.body()).path("jobId").longValue()
            jdbc.update(
                "UPDATE \"AI_LEAD_MANAGER\".ai_jobs SET status = 'FAILED' WHERE id = ?",
                failedJobId,
            )
            val retriedAnalysis = send(client, "POST", "/api/leads/$leadId/ai-analysis/retry", "{}", csrf)
            assertEquals(202, retriedAnalysis.statusCode(), retriedAnalysis.body())
            assertEquals("PENDING", mapper.readTree(retriedAnalysis.body()).path("status").stringValue())
            assertTrue(mapper.readTree(retriedAnalysis.body()).path("jobId").longValue() != failedJobId)

            val invalidNote = send(client, "POST", "/api/leads/$leadId/notes", """{"body":"   "}""", csrf)
            assertEquals(400, invalidNote.statusCode(), invalidNote.body())
            val createdNote = send(
                client,
                "POST",
                "/api/leads/$leadId/notes",
                """{"body":"  Confirm the budget before the call.  "}""",
                csrf,
            )
            assertEquals(201, createdNote.statusCode(), createdNote.body())
            val createdNoteJson = mapper.readTree(createdNote.body())
            assertEquals("Confirm the budget before the call.", createdNoteJson.path("body").stringValue())
            assertEquals(authenticatedUserId, createdNoteJson.path("authorId").longValue())
            val notes = send(client, "GET", "/api/leads/$leadId/notes")
            assertEquals(200, notes.statusCode(), notes.body())
            assertEquals(createdNoteJson.path("id").longValue(), mapper.readTree(notes.body()).first().path("id").longValue())

            val statusChanged = send(
                client,
                "PATCH",
                "/api/leads/$leadId/status",
                """{"status":"IN_PROGRESS","version":$initialVersion}""",
                csrf,
            )
            assertEquals(200, statusChanged.statusCode(), statusChanged.body())
            val changedLead = mapper.readTree(statusChanged.body())
            assertEquals("IN_PROGRESS", changedLead.path("status").stringValue())
            assertEquals(initialVersion + 1, changedLead.path("version").longValue())

            val ownerAssigned = send(
                client,
                "PATCH",
                "/api/leads/$leadId/assignee",
                """{"ownerId":$authenticatedUserId,"version":${initialVersion + 1}}""",
                csrf,
            )
            assertEquals(200, ownerAssigned.statusCode(), ownerAssigned.body())
            val assignedLead = mapper.readTree(ownerAssigned.body())
            assertEquals(authenticatedUserId, assignedLead.path("ownerId").longValue())
            assertEquals(initialVersion + 2, assignedLead.path("version").longValue())

            val events = send(client, "GET", "/api/leads/$leadId/events")
            assertEquals(200, events.statusCode(), events.body())
            val firstEvent = mapper.readTree(events.body()).first()
            assertEquals("OWNER_CHANGED", firstEvent.path("type").stringValue())
            assertTrue(firstEvent.path("oldOwnerId").isNull)
            assertEquals(authenticatedUserId, firstEvent.path("newOwnerId").longValue())
            val statusEvent = mapper.readTree(events.body())[1]
            assertEquals("STATUS_CHANGED", statusEvent.path("type").stringValue())
            assertEquals("NEW", statusEvent.path("oldStatus").stringValue())
            assertEquals("IN_PROGRESS", statusEvent.path("newStatus").stringValue())

            val staleUpdate = send(
                client,
                "PATCH",
                "/api/leads/$leadId/status",
                """{"status":"REJECTED","version":$initialVersion}""",
                csrf,
            )
            assertEquals(409, staleUpdate.statusCode(), staleUpdate.body())

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
