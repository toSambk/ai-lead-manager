package dev.aileadmanager.telegram

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TelegramInitDataVerifierTests {
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private val verifier = TelegramInitDataVerifier("123456:example", Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `accepts signed recent init data`() {
        val verified = verifier.verify(signedData())

        assertEquals(987654321L, verified.telegramUserId)
        assertEquals("Cafe Owner", verified.displayName)
    }

    @Test
    fun `rejects a modified user`() {
        val altered = signedData().replace("Cafe", "Fake")

        assertFailsWith<InvalidTelegramInitData> { verifier.verify(altered) }
    }

    @Test
    fun `rejects expired init data`() {
        val old = signedData(now.minusSeconds(601))

        assertFailsWith<InvalidTelegramInitData> { verifier.verify(old) }
    }

    @Test
    fun `rejects duplicate fields`() {
        assertFailsWith<InvalidTelegramInitData> { verifier.verify(signedData() + "&user=duplicate") }
    }

    @Test
    fun `rejects a future auth date`() {
        assertFailsWith<InvalidTelegramInitData> { verifier.verify(signedData(now.plusSeconds(61))) }
    }

    private fun signedData(authDate: Instant = now): String {
        val fields = mapOf(
            "auth_date" to authDate.epochSecond.toString(),
            "user" to """{"id":987654321,"first_name":"Cafe","last_name":"Owner"}""",
            "query_id" to "example",
        )
        val checkString = fields.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        val secret = hmac("WebAppData".toByteArray(StandardCharsets.UTF_8), "123456:example")
        val hash = HexFormat.of().formatHex(hmac(secret, checkString))
        return (fields + ("hash" to hash)).entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }
}
