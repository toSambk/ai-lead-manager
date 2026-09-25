package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.LeadMessageRepository
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.UserRole

data class AddLeadNoteCommand(
    val leadId: Long,
    val authorId: Long,
    val authorRole: UserRole,
    val body: String,
)

enum class LeadNoteFailure {
    LEAD_NOT_FOUND,
    FORBIDDEN,
    INVALID_BODY,
}

class LeadNoteException(
    val failure: LeadNoteFailure,
    message: String,
) : RuntimeException(message)

class AddLeadNoteUseCase(
    private val leads: LeadRepository,
    private val messages: LeadMessageRepository,
) {
    fun add(command: AddLeadNoteCommand): LeadMessage {
        requireManager(command.authorRole)
        if (leads.findById(command.leadId) == null) {
            throw LeadNoteException(LeadNoteFailure.LEAD_NOT_FOUND, "Lead not found")
        }
        val body = command.body.trim()
        if (body.isEmpty() || body.length > MAX_BODY_LENGTH) {
            throw LeadNoteException(
                LeadNoteFailure.INVALID_BODY,
                "Note must contain between 1 and $MAX_BODY_LENGTH characters",
            )
        }
        return messages.save(LeadMessage(
            leadId = command.leadId,
            senderId = command.authorId,
            kind = LeadMessageKind.INTERNAL_NOTE,
            body = body,
        ))
    }

    companion object {
        const val MAX_BODY_LENGTH = 2000
    }
}

class ListLeadNotesUseCase(
    private val leads: LeadRepository,
    private val messages: LeadMessageRepository,
) {
    fun list(leadId: Long, reader: LeadReader): List<LeadMessage> {
        requireManager(reader.role)
        if (leads.findById(leadId) == null) {
            throw LeadNoteException(LeadNoteFailure.LEAD_NOT_FOUND, "Lead not found")
        }
        return messages.findInternalNotesByLeadId(leadId)
    }
}

private fun requireManager(role: UserRole) {
    if (role == UserRole.CUSTOMER) {
        throw LeadNoteException(LeadNoteFailure.FORBIDDEN, "Internal notes are available only to managers")
    }
}
