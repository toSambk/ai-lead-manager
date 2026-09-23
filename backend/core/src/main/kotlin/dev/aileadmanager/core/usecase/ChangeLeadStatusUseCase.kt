package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.UserRole

data class ChangeLeadStatusCommand(
    val leadId: Long,
    val actorId: Long,
    val actorRole: UserRole,
    val status: LeadStatus,
    val expectedVersion: Long,
)

data class ChangeLeadStatusResult(
    val lead: Lead,
    val event: LeadEvent,
)

enum class ChangeLeadStatusFailure {
    LEAD_NOT_FOUND,
    FORBIDDEN,
    INVALID_TRANSITION,
    VERSION_CONFLICT,
}

class ChangeLeadStatusException(
    val failure: ChangeLeadStatusFailure,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class ChangeLeadStatusUseCase(
    private val leads: LeadRepository,
    private val events: LeadEventRepository,
) {
    fun change(command: ChangeLeadStatusCommand): ChangeLeadStatusResult {
        if (command.actorRole == UserRole.CUSTOMER) {
            throw ChangeLeadStatusException(ChangeLeadStatusFailure.FORBIDDEN, "Only managers can change lead status")
        }
        val lead = leads.findById(command.leadId)
            ?: throw ChangeLeadStatusException(ChangeLeadStatusFailure.LEAD_NOT_FOUND, "Lead not found")
        if (lead.version != command.expectedVersion) {
            throw ChangeLeadStatusException(
                ChangeLeadStatusFailure.VERSION_CONFLICT,
                "Lead was changed by another operation",
            )
        }
        if (command.status !in allowedTransitions.getValue(lead.status)) {
            throw ChangeLeadStatusException(
                ChangeLeadStatusFailure.INVALID_TRANSITION,
                "Lead status cannot change from ${lead.status} to ${command.status}",
            )
        }
        val updated = leads.save(lead.copy(status = command.status))
        val event = events.save(LeadEvent(
            leadId = command.leadId,
            actorId = command.actorId,
            type = LeadEventType.STATUS_CHANGED,
            oldStatus = lead.status,
            newStatus = updated.status,
        ))
        return ChangeLeadStatusResult(updated, event)
    }

    companion object {
        private val allowedTransitions = mapOf(
            LeadStatus.NEW to setOf(LeadStatus.CLARIFICATION, LeadStatus.IN_PROGRESS, LeadStatus.REJECTED),
            LeadStatus.CLARIFICATION to setOf(LeadStatus.NEW, LeadStatus.IN_PROGRESS, LeadStatus.REJECTED),
            LeadStatus.IN_PROGRESS to setOf(LeadStatus.COMPLETED, LeadStatus.REJECTED),
            LeadStatus.COMPLETED to emptySet(),
            LeadStatus.REJECTED to emptySet(),
        )
    }
}
