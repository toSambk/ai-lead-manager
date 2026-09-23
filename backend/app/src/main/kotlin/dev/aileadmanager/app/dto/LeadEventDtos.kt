package dev.aileadmanager.app.dto

import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadStatus
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

data class ChangeLeadStatusRequest(
    val status: LeadStatus,
    @field:PositiveOrZero val version: Long,
)

data class LeadEventResponse(
    val id: Long,
    val type: LeadEventType,
    val actorId: Long?,
    val oldStatus: LeadStatus?,
    val newStatus: LeadStatus?,
    val oldOwnerId: Long?,
    val newOwnerId: Long?,
    val createdAt: Instant,
) {
    companion object {
        fun from(event: LeadEvent) = LeadEventResponse(
            id = checkNotNull(event.id) { "Stored lead event has no ID" },
            type = event.type,
            actorId = event.actorId,
            oldStatus = event.oldStatus,
            newStatus = event.newStatus,
            oldOwnerId = event.oldOwnerId,
            newOwnerId = event.newOwnerId,
            createdAt = checkNotNull(event.createdAt) { "Stored lead event has no creation time" },
        )
    }
}
