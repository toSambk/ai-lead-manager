package dev.aileadmanager.telegram

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

data class VerifiedTelegramUser(val telegramUserId: Long, val displayName: String)

class InvalidTelegramInitData(message: String = "Invalid Telegram Mini App data") : RuntimeException(message)
class TelegramAuthUnavailable : RuntimeException("Telegram bot token is not configured")

class TelegramInitDataVerifier(
    private val botToken: String,
    private val clock: Clock = Clock.systemUTC(),
    private val maxAge: Duration = Duration.ofMinutes(10),
) {
    private val mapper = ObjectMapper()

    fun verify(raw: String): VerifiedTelegramUser {
        if (botToken.isBlank()) throw TelegramAuthUnavailable()
        if (raw.isBlank() || raw.length > 16_384) throw InvalidTelegramInitData()

        val fields = parseFields(raw)
        val receivedHash = try {
            HexFormat.of().parseHex(fields["hash"] ?: throw InvalidTelegramInitData())
        } catch (_: IllegalArgumentException) {
            throw InvalidTelegramInitData()
        }
        if (receivedHash.size != 32) throw InvalidTelegramInitData()

        val checkString = fields.entries.asSequence()
            .filter { it.key != "hash" }
            .sortedBy { it.key }
            .joinToString("\n") { "${it.key}=${it.value}" }
        val secret = hmac("WebAppData".toByteArray(StandardCharsets.UTF_8), botToken)
        val expectedHash = hmac(secret, checkString)
        if (!MessageDigest.isEqual(receivedHash, expectedHash)) {
            throw InvalidTelegramInitData("Telegram init data hash does not match")
        }

        val authDate = try {
            fields["auth_date"]?.toLongOrNull()?.let(Instant::ofEpochSecond)
                ?: throw InvalidTelegramInitData()
        } catch (_: DateTimeException) {
            throw InvalidTelegramInitData()
        }
        val now = clock.instant()
        if (authDate.isBefore(now.minus(maxAge)) || authDate.isAfter(now.plusSeconds(60))) {
            throw InvalidTelegramInitData()
        }

        val user = try {
            mapper.readTree(fields["user"] ?: throw InvalidTelegramInitData())
        } catch (_: JacksonException) {
            throw InvalidTelegramInitData()
        }
        val idNode = user.path("id")
        if (!idNode.isIntegralNumber) throw InvalidTelegramInitData()
        val id = idNode.longValue()
        val name = listOf(user.path("first_name").stringValue(""), user.path("last_name").stringValue(""))
            .filter(String::isNotBlank)
            .joinToString(" ")
            .trim()
        if (id <= 0 || name.isBlank() || name.length > 256) throw InvalidTelegramInitData()
        return VerifiedTelegramUser(id, name)
    }

    private fun parseFields(raw: String): Map<String, String> = buildMap {
        try {
            for (part in raw.split('&')) {
                val pair = part.split('=', limit = 2)
                if (pair.size != 2) throw InvalidTelegramInitData()
                val key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8)
                val value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                if (key.isBlank() || put(key, value) != null) throw InvalidTelegramInitData()
            }
        } catch (_: IllegalArgumentException) {
            throw InvalidTelegramInitData()
        }
    }

    private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }
}
