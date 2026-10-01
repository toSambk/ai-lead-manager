package dev.aileadmanager.app

import dev.aileadmanager.app.service.lead.LeadCreationService
import dev.aileadmanager.app.service.lead.LeadClarificationService
import dev.aileadmanager.app.service.lead.ReplyDraftService
import dev.aileadmanager.app.service.telegram.TelegramDeliveryCompletionService
import dev.aileadmanager.core.TelegramDeliveryJobRepository
import dev.aileadmanager.core.UserRole
import dev.aileadmanager.core.usecase.CreateLeadCommand
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "TELEGRAM_BOT_TOKEN=123456:integration-test",
        "telegram.webhook-secret=integration-webhook-secret",
        "telegram.mini-app-url=https://mini-app.example.test",
        "telegram.manager-chat-id=-1001234567890",
        "telegram.delivery.worker.enabled=false",
        "ai.worker.enabled=false",
    ],
)
@Import(PostgresTestConfiguration::class)
class TelegramWebhookIntegrationTests {
    @Value("\${local.server.port}") private lateinit var port: String
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var leadCreation: LeadCreationService
    @Autowired private lateinit var replyDrafts: ReplyDraftService
    @Autowired private lateinit var clarifications: LeadClarificationService
    @Autowired private lateinit var deliveryCompletion: TelegramDeliveryCompletionService
    @Autowired private lateinit var deliveryJobs: TelegramDeliveryJobRepository
    private val client = HttpClient.newHttpClient()

    @Test
    fun `start update is deduplicated and lead creation queues Telegram deliveries`() {
        val telegramId = ThreadLocalRandom.current().nextLong(1, 8_000_000_000_000)
        val firstUpdateId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 2)
        val secondUpdateId = firstUpdateId + 1
        var leadId: Long? = null
        try {
            val startUpdate = update(firstUpdateId, telegramId, "/start")
            assertEquals(403, postWebhook(startUpdate, "wrong-secret").statusCode())
            assertEquals(200, postWebhook(startUpdate, "integration-webhook-secret").statusCode())
            assertEquals(200, postWebhook(startUpdate, "integration-webhook-secret").statusCode())

            val userId = jdbc.queryForObject(
                "SELECT id FROM \"AI_LEAD_MANAGER\".users WHERE telegram_user_id = ?",
                Long::class.java,
                telegramId,
            )!!
            assertEquals(
                telegramId,
                jdbc.queryForObject(
                    "SELECT telegram_chat_id FROM \"AI_LEAD_MANAGER\".telegram_chat_bindings WHERE user_id = ?",
                    Long::class.java,
                    userId,
                ),
            )
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT count(*) FROM \"AI_LEAD_MANAGER\".telegram_updates WHERE update_id = ?",
                    Int::class.java,
                    firstUpdateId,
                ),
            )
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT count(*) FROM \"AI_LEAD_MANAGER\".telegram_delivery_jobs WHERE message_key = ?",
                    Int::class.java,
                    "START_REPLY:$firstUpdateId",
                ),
            )

            val categoryId = jdbc.queryForObject(
                "SELECT id FROM \"AI_LEAD_MANAGER\".service_categories WHERE code = 'automation'",
                Long::class.java,
            )!!
            val lead = leadCreation.create(CreateLeadCommand(
                customerId = userId,
                categoryId = categoryId,
                description = "Automate appointment reminders",
                contactDetails = "@alice",
            ))
            leadId = lead.id

            val deliveryTypes = jdbc.queryForList(
                """
                SELECT message_type
                FROM "AI_LEAD_MANAGER".telegram_delivery_jobs
                WHERE lead_id = ?
                ORDER BY message_type
                """.trimIndent(),
                String::class.java,
                leadId,
            )
            assertEquals(listOf("CUSTOMER_LEAD_CONFIRMATION", "MANAGER_NEW_LEAD"), deliveryTypes)
            assertTrue(jdbc.queryForObject(
                "SELECT count(*) FROM \"AI_LEAD_MANAGER\".ai_jobs WHERE lead_id = ?",
                Int::class.java,
                leadId,
            ) == 1)

            jdbc.update(
                "UPDATE \"AI_LEAD_MANAGER\".users SET role = 'MANAGER' WHERE id = ?",
                userId,
            )
            assertEquals(
                200,
                postWebhook(update(secondUpdateId, telegramId, "/start"), "integration-webhook-secret").statusCode(),
            )
            assertEquals(
                UserRole.MANAGER.name,
                jdbc.queryForObject(
                    "SELECT role FROM \"AI_LEAD_MANAGER\".users WHERE id = ?",
                    String::class.java,
                    userId,
                ),
            )
        } finally {
            leadId?.let { jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".leads WHERE id = ?", it) }
            jdbc.update(
                "DELETE FROM \"AI_LEAD_MANAGER\".telegram_delivery_jobs WHERE message_key IN (?, ?)",
                "START_REPLY:$firstUpdateId",
                "START_REPLY:$secondUpdateId",
            )
            jdbc.update(
                "DELETE FROM \"AI_LEAD_MANAGER\".telegram_updates WHERE update_id IN (?, ?)",
                firstUpdateId,
                secondUpdateId,
            )
            jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".users WHERE telegram_user_id = ?", telegramId)
        }
    }

    @Test
    fun `approved question and customer reply complete the clarification round trip`() {
        val telegramId = ThreadLocalRandom.current().nextLong(1, 8_000_000_000_000)
        val startUpdateId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 3)
        val replyUpdateId = startUpdateId + 1
        var leadId: Long? = null
        try {
            assertEquals(
                200,
                postWebhook(update(startUpdateId, telegramId, "/start"), "integration-webhook-secret").statusCode(),
            )
            val userId = jdbc.queryForObject(
                "SELECT id FROM \"AI_LEAD_MANAGER\".users WHERE telegram_user_id = ?",
                Long::class.java,
                telegramId,
            )!!
            val categoryId = jdbc.queryForObject(
                "SELECT id FROM \"AI_LEAD_MANAGER\".service_categories WHERE code = 'automation'",
                Long::class.java,
            )!!
            val lead = leadCreation.create(CreateLeadCommand(
                customerId = userId,
                categoryId = categoryId,
                description = "Automate appointment reminders",
                contactDetails = "@alice",
            ))
            leadId = checkNotNull(lead.id)
            jdbc.update("UPDATE \"AI_LEAD_MANAGER\".leads SET status = 'CLARIFICATION' WHERE id = ?", leadId)
            jdbc.update("UPDATE \"AI_LEAD_MANAGER\".ai_jobs SET status = 'SUCCEEDED' WHERE lead_id = ?", leadId)

            val draft = replyDrafts.create(
                leadId,
                userId,
                UserRole.MANAGER,
                "What budget and deadline do you expect?",
            )
            val approved = clarifications.approveAndSend(
                leadId,
                checkNotNull(draft.id),
                UserRole.MANAGER,
                draft.version,
            )
            val delivery = checkNotNull(deliveryJobs.findByMessageKey("REPLY_DRAFT:${approved.id}"))
            jdbc.update(
                """
                UPDATE "AI_LEAD_MANAGER".telegram_delivery_jobs
                SET status = 'RUNNING', attempt_count = 1, locked_at = now(),
                    locked_until = now() + interval '2 minutes', locked_by = 'integration-test'
                WHERE id = ?
                """.trimIndent(),
                delivery.id,
            )
            deliveryCompletion.complete(checkNotNull(deliveryJobs.findByMessageKey(delivery.messageKey)), 901)

            assertEquals(
                200,
                postWebhook(
                    update(replyUpdateId, telegramId, "1500 USD, deadline 2026-12-01"),
                    "integration-webhook-secret",
                ).statusCode(),
            )
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT count(*) FROM \"AI_LEAD_MANAGER\".lead_messages WHERE lead_id = ? AND kind = 'CUSTOMER_REPLY'",
                    Int::class.java,
                    leadId,
                ),
            )
            assertEquals(
                "ANSWERED",
                jdbc.queryForObject(
                    "SELECT status FROM \"AI_LEAD_MANAGER\".telegram_conversations WHERE lead_id = ?",
                    String::class.java,
                    leadId,
                ),
            )
            assertEquals(
                1,
                jdbc.queryForObject(
                    "SELECT count(*) FROM \"AI_LEAD_MANAGER\".ai_jobs WHERE lead_id = ? AND status = 'PENDING'",
                    Int::class.java,
                    leadId,
                ),
            )
        } finally {
            leadId?.let { jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".leads WHERE id = ?", it) }
            jdbc.update(
                "DELETE FROM \"AI_LEAD_MANAGER\".telegram_delivery_jobs WHERE message_key = ?",
                "START_REPLY:$startUpdateId",
            )
            jdbc.update(
                "DELETE FROM \"AI_LEAD_MANAGER\".telegram_updates WHERE update_id IN (?, ?)",
                startUpdateId,
                replyUpdateId,
            )
            jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".users WHERE telegram_user_id = ?", telegramId)
        }
    }

    private fun postWebhook(body: String, secret: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/telegram/webhook"))
            .header("Content-Type", "application/json")
            .header("X-Telegram-Bot-Api-Secret-Token", secret)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun update(updateId: Long, telegramId: Long, text: String) =
        """
        {
          "update_id": $updateId,
          "message": {
            "message_id": 10,
            "from": {"id": $telegramId, "first_name": "Alice"},
            "chat": {"id": $telegramId, "type": "private"},
            "text": "$text"
          }
        }
        """.trimIndent()
}
