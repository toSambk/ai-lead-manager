package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.UserRole

data class AssignLeadOwnerCommand(
    val leadId: Long,
    val actorId: Long,
    val actorRole: UserRole,
    val ownerId: Long?,
    val expectedVersion: Long,
)

data class AssignLeadOwnerResult(
    val lead: Lead,
    val event: LeadEvent,
)

enum class AssignLeadOwnerFailure {
    LEAD_NOT_FOUND,
    FORBIDDEN,
    OWNER_UNAVAILABLE,
    OWNER_UNCHANGED,
    VERSION_CONFLICT,
}

class AssignLeadOwnerException(
    val failure: AssignLeadOwnerFailure,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AssignLeadOwnerUseCase(
    private val leads: LeadRepository,
    private val users: UserRepository,
    private val events: LeadEventRepository,
) {
    fun assign(command: AssignLeadOwnerCommand): AssignLeadOwnerResult {
        if (command.actorRole == UserRole.CUSTOMER) {
            throw AssignLeadOwnerException(AssignLeadOwnerFailure.FORBIDDEN, "Only managers can assign lead owners")
        }
        val lead = leads.findById(command.leadId)
            ?: throw AssignLeadOwnerException(AssignLeadOwnerFailure.LEAD_NOT_FOUND, "Lead not found")
        if (lead.version != command.expectedVersion) {
            throw AssignLeadOwnerException(
                AssignLeadOwnerFailure.VERSION_CONFLICT,
                "Lead was changed by another operation",
            )
        }
        command.ownerId?.let { ownerId ->
            val owner = users.findById(ownerId)
            if (owner == null || owner.role == UserRole.CUSTOMER) {
                throw AssignLeadOwnerException(
                    AssignLeadOwnerFailure.OWNER_UNAVAILABLE,
                    "Selected user cannot own leads",
                )
            }
        }
        if (lead.ownerId == command.ownerId) {
            throw AssignLeadOwnerException(AssignLeadOwnerFailure.OWNER_UNCHANGED, "Lead owner is unchanged")
        }

        val updated = leads.save(lead.copy(ownerId = command.ownerId))
        val event = events.save(LeadEvent(
            leadId = command.leadId,
            actorId = command.actorId,
            type = LeadEventType.OWNER_CHANGED,
            oldOwnerId = lead.ownerId,
            newOwnerId = updated.ownerId,
        ))
        return AssignLeadOwnerResult(updated, event)
    }
}
