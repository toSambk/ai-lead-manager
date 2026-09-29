package dev.aileadmanager.core

import java.time.Instant

enum class ReplyDraftStatus { DRAFT, APPROVED, SENT, FAILED }

data class ReplyDraft(
    val id: Long? = null,
    val leadId: Long,
    val authorId: Long,
    val body: String,
    val status: ReplyDraftStatus = ReplyDraftStatus.DRAFT,
    val version: Long = 0,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val approvedAt: Instant? = null,
    val sentAt: Instant? = null,
)

interface ReplyDraftRepository {
    fun create(draft: ReplyDraft): ReplyDraft
    fun update(draft: ReplyDraft, expectedVersion: Long): ReplyDraft?
    fun findById(id: Long): ReplyDraft?
    fun findByLeadId(leadId: Long): List<ReplyDraft>
    fun markSent(id: Long, sentAt: Instant)
    fun markFailed(id: Long, updatedAt: Instant)
}

enum class TelegramConversationStatus { PENDING_DELIVERY, AWAITING_REPLY, ANSWERED, CANCELLED }

data class TelegramConversation(
    val id: Long? = null,
    val customerId: Long,
    val leadId: Long,
    val questionMessageId: Long,
    val status: TelegramConversationStatus = TelegramConversationStatus.PENDING_DELIVERY,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val answeredAt: Instant? = null,
)

interface TelegramConversationRepository {
    fun create(conversation: TelegramConversation): TelegramConversation
    fun findAwaitingReplyByCustomerId(customerId: Long): TelegramConversation?
    fun markAwaitingReply(id: Long, now: Instant)
    fun markAnswered(id: Long, now: Instant)
    fun markCancelled(id: Long, now: Instant)
}
