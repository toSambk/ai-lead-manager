package dev.aileadmanager.core

import java.time.Instant

enum class LeadEventType {
    STATUS_CHANGED,
    OWNER_CHANGED,
}

data class LeadEvent(
    val id: Long? = null,
    val leadId: Long,
    val actorId: Long?,
    val type: LeadEventType,
    val oldStatus: LeadStatus? = null,
    val newStatus: LeadStatus? = null,
    val oldOwnerId: Long? = null,
    val newOwnerId: Long? = null,
    val createdAt: Instant? = null,
)

interface LeadEventRepository {
    fun save(event: LeadEvent): LeadEvent
    fun findByLeadId(leadId: Long): List<LeadEvent>
}
