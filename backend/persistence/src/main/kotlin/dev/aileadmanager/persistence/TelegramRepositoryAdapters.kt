package dev.aileadmanager.persistence

import dev.aileadmanager.core.TelegramChatBinding
import dev.aileadmanager.core.TelegramChatBindingRepository
import dev.aileadmanager.core.TelegramDeliveryErrorCode
import dev.aileadmanager.core.TelegramDeliveryJob
import dev.aileadmanager.core.TelegramDeliveryJobRepository
import dev.aileadmanager.core.TelegramDeliveryStatus
import dev.aileadmanager.core.TelegramDeliveryType
import dev.aileadmanager.core.TelegramUpdateRepository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
internal class JdbcTelegramUpdateRepository(private val jdbc: JdbcTemplate) : TelegramUpdateRepository {
    override fun recordIfNew(updateId: Long, updateType: String, payload: String, receivedAt: Instant): Boolean =
        jdbc.update(
            """
            INSERT INTO "AI_LEAD_MANAGER".telegram_updates (update_id, update_type, payload, received_at)
            VALUES (?, ?, ?::jsonb, ?)
            ON CONFLICT (update_id) DO NOTHING
            """.trimIndent(),
            updateId,
            updateType,
            payload,
            Timestamp.from(receivedAt),
        ) == 1

    override fun markProcessed(updateId: Long, processedAt: Instant) {
        val changed = jdbc.update(
            """
            UPDATE "AI_LEAD_MANAGER".telegram_updates
            SET processed_at = ?
            WHERE update_id = ? AND processed_at IS NULL
            """.trimIndent(),
            Timestamp.from(processedAt),
            updateId,
        )
        check(changed == 1) { "Telegram update $updateId is unavailable or already processed" }
    }
}

@Repository
internal class JdbcTelegramChatBindingRepository(private val jdbc: JdbcTemplate) : TelegramChatBindingRepository {
    override fun savePrivateChat(userId: Long, telegramChatId: Long, now: Instant): TelegramChatBinding =
        requireNotNull(jdbc.queryForObject(
            """
            INSERT INTO "AI_LEAD_MANAGER".telegram_chat_bindings (
                user_id, telegram_chat_id, chat_type, created_at, updated_at
            ) VALUES (?, ?, 'PRIVATE', ?, ?)
            ON CONFLICT (user_id) DO UPDATE
            SET telegram_chat_id = EXCLUDED.telegram_chat_id, updated_at = EXCLUDED.updated_at
            RETURNING user_id, telegram_chat_id, created_at, updated_at
            """.trimIndent(),
            ::mapBinding,
            userId,
            telegramChatId,
            Timestamp.from(now),
            Timestamp.from(now),
        ))

    override fun findPrivateChatByUserId(userId: Long): TelegramChatBinding? = jdbc.query(
        """
        SELECT user_id, telegram_chat_id, created_at, updated_at
        FROM "AI_LEAD_MANAGER".telegram_chat_bindings
        WHERE user_id = ? AND chat_type = 'PRIVATE'
        """.trimIndent(),
        ::mapBinding,
        userId,
    ).firstOrNull()

    private fun mapBinding(result: ResultSet, row: Int) = TelegramChatBinding(
        userId = result.getLong("user_id"),
        telegramChatId = result.getLong("telegram_chat_id"),
        createdAt = result.getTimestamp("created_at").toInstant(),
        updatedAt = result.getTimestamp("updated_at").toInstant(),
    )
}

@Repository
internal class JdbcTelegramDeliveryJobRepository(private val jdbc: JdbcTemplate) : TelegramDeliveryJobRepository {
    override fun enqueue(job: TelegramDeliveryJob): TelegramDeliveryJob {
        val inserted = jdbc.query(
            """
            INSERT INTO "AI_LEAD_MANAGER".telegram_delivery_jobs (
                message_key, message_type, chat_id, lead_id, reply_draft_id, lead_message_id, conversation_id,
                message_text, button_text, button_url,
                status, attempt_count, max_attempts, next_attempt_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
            ON CONFLICT (message_key) DO NOTHING
            RETURNING *
            """.trimIndent(),
            ::mapJob,
            job.messageKey,
            job.type.name,
            job.chatId,
            job.leadId,
            job.replyDraftId,
            job.leadMessageId,
            job.conversationId,
            job.text,
            job.buttonText,
            job.buttonUrl,
            job.maxAttempts,
            Timestamp.from(job.nextAttemptAt),
            Timestamp.from(job.createdAt ?: job.nextAttemptAt),
            Timestamp.from(job.updatedAt ?: job.nextAttemptAt),
        ).firstOrNull()
        return inserted ?: requireNotNull(findByMessageKey(job.messageKey))
    }

    override fun findByMessageKey(messageKey: String): TelegramDeliveryJob? = jdbc.query(
        "SELECT * FROM \"AI_LEAD_MANAGER\".telegram_delivery_jobs WHERE message_key = ?",
        ::mapJob,
        messageKey,
    ).firstOrNull()

    override fun claimAvailable(
        workerId: String,
        now: Instant,
        lockedUntil: Instant,
        limit: Int,
    ): List<TelegramDeliveryJob> = jdbc.query(
        """
        WITH candidates AS (
            SELECT id
            FROM "AI_LEAD_MANAGER".telegram_delivery_jobs
            WHERE status = 'PENDING'
              AND next_attempt_at <= ?
              AND attempt_count < max_attempts
            ORDER BY next_attempt_at, created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT ?
        )
        UPDATE "AI_LEAD_MANAGER".telegram_delivery_jobs job
        SET status = 'RUNNING',
            attempt_count = attempt_count + 1,
            locked_at = ?,
            locked_until = ?,
            locked_by = ?,
            updated_at = ?
        FROM candidates
        WHERE job.id = candidates.id
        RETURNING job.*
        """.trimIndent(),
        ::mapJob,
        Timestamp.from(now),
        limit,
        Timestamp.from(now),
        Timestamp.from(lockedUntil),
        workerId,
        Timestamp.from(now),
    )

    override fun markSucceeded(jobId: Long, telegramMessageId: Long, now: Instant) {
        updateRunning(
            jobId,
            """
            status = 'SUCCEEDED', telegram_message_id = ?, delivered_at = ?,
            locked_at = NULL, locked_until = NULL, locked_by = NULL,
            last_error_code = NULL, last_error_message = NULL, updated_at = ?
            """.trimIndent(),
            telegramMessageId,
            Timestamp.from(now),
            Timestamp.from(now),
        )
    }

    override fun reschedule(
        jobId: Long,
        nextAttemptAt: Instant,
        errorCode: TelegramDeliveryErrorCode,
        errorMessage: String,
        now: Instant,
    ) {
        updateRunning(
            jobId,
            """
            status = 'PENDING', next_attempt_at = ?, locked_at = NULL, locked_until = NULL, locked_by = NULL,
            last_error_code = ?, last_error_message = ?, updated_at = ?
            """.trimIndent(),
            Timestamp.from(nextAttemptAt),
            errorCode.name,
            errorMessage.take(1000),
            Timestamp.from(now),
        )
    }

    override fun markFailed(
        jobId: Long,
        errorCode: TelegramDeliveryErrorCode,
        errorMessage: String,
        now: Instant,
    ) {
        updateRunning(
            jobId,
            """
            status = 'FAILED', locked_at = NULL, locked_until = NULL, locked_by = NULL,
            last_error_code = ?, last_error_message = ?, updated_at = ?
            """.trimIndent(),
            errorCode.name,
            errorMessage.take(1000),
            Timestamp.from(now),
        )
    }

    override fun recoverStale(now: Instant): List<TelegramDeliveryJob> = jdbc.query(
        """
        UPDATE "AI_LEAD_MANAGER".telegram_delivery_jobs
        SET status = CASE WHEN attempt_count >= max_attempts THEN 'FAILED' ELSE 'PENDING' END,
            next_attempt_at = ?, locked_at = NULL, locked_until = NULL, locked_by = NULL,
            last_error_code = 'INTERNAL_ERROR',
            last_error_message = 'Worker lease expired before completion',
            updated_at = ?
        WHERE status = 'RUNNING' AND locked_until < ?
        RETURNING *
        """.trimIndent(),
        ::mapJob,
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now),
    )

    private fun updateRunning(jobId: Long, assignments: String, vararg parameters: Any?) {
        val arguments = parameters.toMutableList().apply { add(jobId) }.toTypedArray()
        val changed = jdbc.update(
            "UPDATE \"AI_LEAD_MANAGER\".telegram_delivery_jobs SET $assignments WHERE id = ? AND status = 'RUNNING'",
            *arguments,
        )
        check(changed == 1) { "Telegram delivery job $jobId is not running" }
    }

    private fun mapJob(result: ResultSet, row: Int) = TelegramDeliveryJob(
        id = result.getLong("id"),
        messageKey = result.getString("message_key"),
        type = TelegramDeliveryType.valueOf(result.getString("message_type")),
        chatId = result.getLong("chat_id"),
        leadId = result.getLong("lead_id").takeUnless { result.wasNull() },
        replyDraftId = result.getLong("reply_draft_id").takeUnless { result.wasNull() },
        leadMessageId = result.getLong("lead_message_id").takeUnless { result.wasNull() },
        conversationId = result.getLong("conversation_id").takeUnless { result.wasNull() },
        text = result.getString("message_text"),
        buttonText = result.getString("button_text"),
        buttonUrl = result.getString("button_url"),
        status = TelegramDeliveryStatus.valueOf(result.getString("status")),
        attemptCount = result.getInt("attempt_count"),
        maxAttempts = result.getInt("max_attempts"),
        nextAttemptAt = result.getTimestamp("next_attempt_at").toInstant(),
        lockedAt = result.getTimestamp("locked_at")?.toInstant(),
        lockedUntil = result.getTimestamp("locked_until")?.toInstant(),
        lockedBy = result.getString("locked_by"),
        lastErrorCode = result.getString("last_error_code")?.let(TelegramDeliveryErrorCode::valueOf),
        lastErrorMessage = result.getString("last_error_message"),
        telegramMessageId = result.getLong("telegram_message_id").takeUnless { result.wasNull() },
        createdAt = result.getTimestamp("created_at").toInstant(),
        updatedAt = result.getTimestamp("updated_at").toInstant(),
        deliveredAt = result.getTimestamp("delivered_at")?.toInstant(),
    )
}
