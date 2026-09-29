package dev.aileadmanager.telegram

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

data class TelegramIncomingUser(
    val id: Long,
    val displayName: String,
)

data class TelegramIncomingChat(
    val id: Long,
    val type: String,
)

data class TelegramIncomingMessage(
    val id: Long,
    val from: TelegramIncomingUser?,
    val chat: TelegramIncomingChat,
    val text: String?,
)

data class TelegramIncomingUpdate(
    val id: Long,
    val type: String,
    val message: TelegramIncomingMessage?,
)

class InvalidTelegramUpdate(message: String = "Invalid Telegram update") : RuntimeException(message)
class TelegramWebhookUnavailable : RuntimeException("Telegram webhook secret is not configured")
class InvalidTelegramWebhookSecret : RuntimeException("Invalid Telegram webhook secret")

class TelegramWebhookSecretVerifier(private val configuredSecret: String) {
    fun verify(receivedSecret: String?) {
        if (configuredSecret.isBlank()) throw TelegramWebhookUnavailable()
        val received = receivedSecret?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val expected = configuredSecret.toByteArray(StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(received, expected)) throw InvalidTelegramWebhookSecret()
    }
}

class TelegramUpdateParser {
    private val mapper = ObjectMapper()

    fun parse(raw: String): TelegramIncomingUpdate {
        if (raw.isBlank() || raw.length > MAX_UPDATE_LENGTH) throw InvalidTelegramUpdate()
        val root = try {
            mapper.readTree(raw)
        } catch (_: JacksonException) {
            throw InvalidTelegramUpdate()
        }
        if (!root.isObject) throw InvalidTelegramUpdate()
        val updateIdNode = root.path("update_id")
        if (!updateIdNode.isIntegralNumber || updateIdNode.longValue() < 0) throw InvalidTelegramUpdate()

        val messageNode = root.path("message")
        val message = if (messageNode.isObject) parseMessage(messageNode) else null
        val type = when {
            message != null -> "MESSAGE"
            root.path("callback_query").isObject -> "CALLBACK_QUERY"
            root.path("edited_message").isObject -> "EDITED_MESSAGE"
            else -> "UNSUPPORTED"
        }
        return TelegramIncomingUpdate(updateIdNode.longValue(), type, message)
    }

    private fun parseMessage(node: tools.jackson.databind.JsonNode): TelegramIncomingMessage {
        val messageId = node.path("message_id")
        val chatNode = node.path("chat")
        val chatId = chatNode.path("id")
        val chatType = chatNode.path("type").stringValue("")
        if (!messageId.isIntegralNumber || !chatId.isIntegralNumber || chatType.isBlank()) {
            throw InvalidTelegramUpdate()
        }
        val fromNode = node.path("from")
        val from = if (fromNode.isObject) {
            val idNode = fromNode.path("id")
            if (!idNode.isIntegralNumber || idNode.longValue() <= 0) throw InvalidTelegramUpdate()
            val name = listOf(
                fromNode.path("first_name").stringValue(""),
                fromNode.path("last_name").stringValue(""),
            ).filter(String::isNotBlank).joinToString(" ").trim()
                .ifBlank { fromNode.path("username").stringValue("").trim() }
            if (name.isBlank() || name.length > 256) throw InvalidTelegramUpdate()
            TelegramIncomingUser(idNode.longValue(), name)
        } else {
            null
        }
        val textNode = node.path("text")
        val text = textNode.takeIf { it.isTextual }?.stringValue()?.take(MAX_TEXT_LENGTH)
        return TelegramIncomingMessage(
            id = messageId.longValue(),
            from = from,
            chat = TelegramIncomingChat(chatId.longValue(), chatType),
            text = text,
        )
    }

    private companion object {
        const val MAX_UPDATE_LENGTH = 1_048_576
        const val MAX_TEXT_LENGTH = 4096
    }
}

fun TelegramIncomingMessage.botCommand(): String? {
    val firstToken = text?.trim()?.substringBefore(' ') ?: return null
    if (!firstToken.startsWith('/')) return null
    return firstToken.substringBefore('@').lowercase()
}
