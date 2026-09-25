package dev.aileadmanager.app

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import tools.jackson.databind.ObjectMapper

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.profiles.active=local",
        "server.servlet.session.cookie.secure=false",
        "ai.worker.enabled=false",
    ],
)
@Import(PostgresTestConfiguration::class)
class DevAuthenticationIntegrationTests {
    @Value("\${local.server.port}") private lateinit var port: String
    private val mapper = ObjectMapper()

    @Test
    fun `local profile exposes test users and allows switching sessions`() {
        val client = HttpClient.newBuilder()
            .cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build()

        val availableUsers = send(client, "GET", "/api/dev/auth/users")
        assertEquals(200, availableUsers.statusCode(), availableUsers.body())
        val users = mapper.readTree(availableUsers.body())
        assertEquals(5, users.size())
        assertTrue(users.any { it.path("key").stringValue() == "customer-alice" && it.path("role").stringValue() == "CUSTOMER" })
        assertTrue(users.any { it.path("key").stringValue() == "manager-mike" && it.path("role").stringValue() == "MANAGER" })

        val customerLogin = send(
            client,
            "POST",
            "/api/dev/auth/login",
            """{"userKey":"customer-alice"}""",
            getCsrf(client),
        )
        assertEquals(200, customerLogin.statusCode(), customerLogin.body())
        assertEquals("CUSTOMER", mapper.readTree(customerLogin.body()).path("role").stringValue())
        assertEquals("CUSTOMER", mapper.readTree(send(client, "GET", "/api/me").body()).path("role").stringValue())

        val managerLogin = send(
            client,
            "POST",
            "/api/dev/auth/login",
            """{"userKey":"manager-mike"}""",
            getCsrf(client),
        )
        assertEquals(200, managerLogin.statusCode(), managerLogin.body())
        assertEquals("MANAGER", mapper.readTree(managerLogin.body()).path("role").stringValue())
        assertEquals("MANAGER", mapper.readTree(send(client, "GET", "/api/me").body()).path("role").stringValue())

        val managers = send(client, "GET", "/api/managers")
        assertEquals(200, managers.statusCode(), managers.body())
        assertTrue(mapper.readTree(managers.body()).any { it.path("displayName").stringValue() == "Kate Manager" })

        val unknownLogin = send(
            client,
            "POST",
            "/api/dev/auth/login",
            """{"userKey":"unknown"}""",
            getCsrf(client),
        )
        assertEquals(404, unknownLogin.statusCode(), unknownLogin.body())
    }

    private fun getCsrf(client: HttpClient): String {
        val response = send(client, "GET", "/api/auth/csrf")
        assertEquals(200, response.statusCode(), response.body())
        return mapper.readTree(response.body()).path("token").stringValue()
    }

    private fun send(
        client: HttpClient,
        method: String,
        path: String,
        body: String = "",
        csrf: String? = null,
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .header("Content-Type", "application/json")
            .apply { if (csrf != null) header("X-CSRF-TOKEN", csrf) }
            .method(method, if (method == "GET") HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
