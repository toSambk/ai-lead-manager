package dev.aileadmanager.core

import java.time.Instant

enum class LeadMessageKind {
    INTERNAL_NOTE,
    CUSTOMER_REPLY,
    MANAGER_REPLY,
    FOLLOW_UP_QUESTION,
}

enum class LeadMessageDeliveryStatus { PENDING, SENT, FAILED }

data class LeadMessage(
    val id: Long? = null,
    val leadId: Long,
    val senderId: Long?,
    val kind: LeadMessageKind,
    val body: String,
    val deliveryStatus: LeadMessageDeliveryStatus? = null,
    val telegramChatId: Long? = null,
    val telegramMessageId: Long? = null,
    val createdAt: Instant? = null,
)

interface LeadMessageRepository {
    fun save(message: LeadMessage): LeadMessage
    fun findInternalNotesByLeadId(leadId: Long): List<LeadMessage>
    fun findConversationByLeadId(leadId: Long): List<LeadMessage>
    fun markDeliverySucceeded(messageId: Long, telegramChatId: Long, telegramMessageId: Long)
    fun markDeliveryFailed(messageId: Long)
}
