package dev.aileadmanager.core

import java.time.Instant

enum class LeadMessageKind {
    INTERNAL_NOTE,
    CUSTOMER_REPLY,
    MANAGER_REPLY,
    FOLLOW_UP_QUESTION,
}

data class LeadMessage(
    val id: Long? = null,
    val leadId: Long,
    val senderId: Long?,
    val kind: LeadMessageKind,
    val body: String,
    val createdAt: Instant? = null,
)

interface LeadMessageRepository {
    fun save(message: LeadMessage): LeadMessage
    fun findInternalNotesByLeadId(leadId: Long): List<LeadMessage>
}
