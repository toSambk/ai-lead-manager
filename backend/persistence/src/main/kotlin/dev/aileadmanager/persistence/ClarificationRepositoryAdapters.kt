package dev.aileadmanager.persistence

import dev.aileadmanager.core.ReplyDraft
import dev.aileadmanager.core.ReplyDraftRepository
import dev.aileadmanager.core.ReplyDraftStatus
import dev.aileadmanager.core.TelegramConversation
import dev.aileadmanager.core.TelegramConversationRepository
import dev.aileadmanager.core.TelegramConversationStatus
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
internal class JdbcReplyDraftRepository(private val jdbc: JdbcTemplate) : ReplyDraftRepository {
    override fun create(draft: ReplyDraft): ReplyDraft = requireNotNull(jdbc.queryForObject(
        """
        INSERT INTO "AI_LEAD_MANAGER".reply_drafts (
            lead_id, author_id, body, status, version, created_at, updated_at, approved_at, sent_at
        ) VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?)
        RETURNING *
        """.trimIndent(),
        ::mapDraft,
        draft.leadId,
        draft.authorId,
        draft.body,
        draft.status.name,
        Timestamp.from(checkNotNull(draft.createdAt)),
        Timestamp.from(checkNotNull(draft.updatedAt)),
        draft.approvedAt?.let(Timestamp::from),
        draft.sentAt?.let(Timestamp::from),
    ))

    override fun update(draft: ReplyDraft, expectedVersion: Long): ReplyDraft? = jdbc.query(
        """
        UPDATE "AI_LEAD_MANAGER".reply_drafts
        SET body = ?, status = ?, version = version + 1, updated_at = ?, approved_at = ?, sent_at = ?
        WHERE id = ? AND version = ?
        RETURNING *
        """.trimIndent(),
        ::mapDraft,
        draft.body,
        draft.status.name,
        Timestamp.from(checkNotNull(draft.updatedAt)),
        draft.approvedAt?.let(Timestamp::from),
        draft.sentAt?.let(Timestamp::from),
        checkNotNull(draft.id),
        expectedVersion,
    ).firstOrNull()

    override fun findById(id: Long): ReplyDraft? = jdbc.query(
        "SELECT * FROM \"AI_LEAD_MANAGER\".reply_drafts WHERE id = ?",
        ::mapDraft,
        id,
    ).firstOrNull()

    override fun findByLeadId(leadId: Long): List<ReplyDraft> = jdbc.query(
        """
        SELECT * FROM "AI_LEAD_MANAGER".reply_drafts
        WHERE lead_id = ?
        ORDER BY created_at DESC, id DESC
        """.trimIndent(),
        ::mapDraft,
        leadId,
    )

    override fun markSent(id: Long, sentAt: Instant) {
        val changed = jdbc.update(
            """
            UPDATE "AI_LEAD_MANAGER".reply_drafts
            SET status = 'SENT', version = version + 1, updated_at = ?, sent_at = ?
            WHERE id = ? AND status = 'APPROVED'
            """.trimIndent(),
            Timestamp.from(sentAt),
            Timestamp.from(sentAt),
            id,
        )
        check(changed == 1) { "Reply draft $id is not approved" }
    }

    override fun markFailed(id: Long, updatedAt: Instant) {
        val changed = jdbc.update(
            """
            UPDATE "AI_LEAD_MANAGER".reply_drafts
            SET status = 'FAILED', version = version + 1, updated_at = ?
            WHERE id = ? AND status = 'APPROVED'
            """.trimIndent(),
            Timestamp.from(updatedAt),
            id,
        )
        check(changed == 1) { "Reply draft $id is not approved" }
    }

    private fun mapDraft(result: ResultSet, row: Int) = ReplyDraft(
        id = result.getLong("id"),
        leadId = result.getLong("lead_id"),
        authorId = result.getLong("author_id"),
        body = result.getString("body"),
        status = ReplyDraftStatus.valueOf(result.getString("status")),
        version = result.getLong("version"),
        createdAt = result.getTimestamp("created_at").toInstant(),
        updatedAt = result.getTimestamp("updated_at").toInstant(),
        approvedAt = result.getTimestamp("approved_at")?.toInstant(),
        sentAt = result.getTimestamp("sent_at")?.toInstant(),
    )
}

@Repository
internal class JdbcTelegramConversationRepository(private val jdbc: JdbcTemplate) : TelegramConversationRepository {
    override fun create(conversation: TelegramConversation): TelegramConversation = requireNotNull(jdbc.queryForObject(
        """
        INSERT INTO "AI_LEAD_MANAGER".telegram_conversations (
            customer_id, lead_id, question_message_id, status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?)
        RETURNING *
        """.trimIndent(),
        ::mapConversation,
        conversation.customerId,
        conversation.leadId,
        conversation.questionMessageId,
        conversation.status.name,
        Timestamp.from(checkNotNull(conversation.createdAt)),
        Timestamp.from(checkNotNull(conversation.updatedAt)),
    ))

    override fun findAwaitingReplyByCustomerId(customerId: Long): TelegramConversation? = jdbc.query(
        """
        SELECT * FROM "AI_LEAD_MANAGER".telegram_conversations
        WHERE customer_id = ? AND status = 'AWAITING_REPLY'
        """.trimIndent(),
        ::mapConversation,
        customerId,
    ).firstOrNull()

    override fun markAwaitingReply(id: Long, now: Instant) = updateStatus(
        id,
        TelegramConversationStatus.PENDING_DELIVERY,
        TelegramConversationStatus.AWAITING_REPLY,
        now,
    )

    override fun markAnswered(id: Long, now: Instant) {
        val changed = jdbc.update(
            """
            UPDATE "AI_LEAD_MANAGER".telegram_conversations
            SET status = 'ANSWERED', updated_at = ?, answered_at = ?
            WHERE id = ? AND status = 'AWAITING_REPLY'
            """.trimIndent(),
            Timestamp.from(now),
            Timestamp.from(now),
            id,
        )
        check(changed == 1) { "Telegram conversation $id is not awaiting a reply" }
    }

    override fun markCancelled(id: Long, now: Instant) {
        val changed = jdbc.update(
            """
            UPDATE "AI_LEAD_MANAGER".telegram_conversations
            SET status = 'CANCELLED', updated_at = ?
            WHERE id = ? AND status IN ('PENDING_DELIVERY', 'AWAITING_REPLY')
            """.trimIndent(),
            Timestamp.from(now),
            id,
        )
        check(changed == 1) { "Telegram conversation $id is not active" }
    }

    private fun updateStatus(
        id: Long,
        expected: TelegramConversationStatus,
        target: TelegramConversationStatus,
        now: Instant,
    ) {
        val changed = jdbc.update(
            """
            UPDATE "AI_LEAD_MANAGER".telegram_conversations
            SET status = ?, updated_at = ?
            WHERE id = ? AND status = ?
            """.trimIndent(),
            target.name,
            Timestamp.from(now),
            id,
            expected.name,
        )
        check(changed == 1) { "Telegram conversation $id is not ${expected.name.lowercase()}" }
    }

    private fun mapConversation(result: ResultSet, row: Int) = TelegramConversation(
        id = result.getLong("id"),
        customerId = result.getLong("customer_id"),
        leadId = result.getLong("lead_id"),
        questionMessageId = result.getLong("question_message_id"),
        status = TelegramConversationStatus.valueOf(result.getString("status")),
        createdAt = result.getTimestamp("created_at").toInstant(),
        updatedAt = result.getTimestamp("updated_at").toInstant(),
        answeredAt = result.getTimestamp("answered_at")?.toInstant(),
    )
}
