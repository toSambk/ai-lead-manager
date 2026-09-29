package dev.aileadmanager.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TelegramWebhookTests {
    @Test
    fun `verifies configured webhook secret`() {
        val verifier = TelegramWebhookSecretVerifier("expected-secret")

        verifier.verify("expected-secret")
        assertFailsWith<InvalidTelegramWebhookSecret> { verifier.verify("wrong-secret") }
        assertFailsWith<InvalidTelegramWebhookSecret> { verifier.verify(null) }
        assertFailsWith<TelegramWebhookUnavailable> { TelegramWebhookSecretVerifier("").verify("value") }
    }

    @Test
    fun `parses a private start command addressed to the bot`() {
        val update = TelegramUpdateParser().parse(
            """
            {
              "update_id": 901,
              "message": {
                "message_id": 17,
                "from": {"id": 12345, "first_name": "Alice", "last_name": "Customer"},
                "chat": {"id": 12345, "type": "private"},
                "text": "/start@ai_lead_manager_bot campaign"
              }
            }
            """.trimIndent(),
        )

        assertEquals(901, update.id)
        assertEquals("MESSAGE", update.type)
        assertEquals("Alice Customer", update.message?.from?.displayName)
        assertEquals("/start", update.message?.botCommand())
    }

    @Test
    fun `accepts unsupported update types without fabricating a message`() {
        val update = TelegramUpdateParser().parse(
            """{"update_id":902,"my_chat_member":{"chat":{"id":1}}}""",
        )

        assertEquals("UNSUPPORTED", update.type)
        assertNull(update.message)
    }

    @Test
    fun `rejects malformed updates`() {
        val parser = TelegramUpdateParser()

        assertFailsWith<InvalidTelegramUpdate> { parser.parse("not-json") }
        assertFailsWith<InvalidTelegramUpdate> { parser.parse("{}") }
        assertFailsWith<InvalidTelegramUpdate> {
            parser.parse("""{"update_id":1,"message":{"message_id":2,"chat":{}}}""")
        }
    }
}
