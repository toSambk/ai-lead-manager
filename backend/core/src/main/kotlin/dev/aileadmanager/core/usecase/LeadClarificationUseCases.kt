package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageRepository
import dev.aileadmanager.core.LeadMessageDeliveryStatus
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ReplyDraft
import dev.aileadmanager.core.ReplyDraftRepository
import dev.aileadmanager.core.ReplyDraftStatus
import dev.aileadmanager.core.UserRole
import java.time.Instant

enum class LeadClarificationFailure {
    LEAD_NOT_FOUND,
    DRAFT_NOT_FOUND,
    FORBIDDEN,
    INVALID_BODY,
    INVALID_LEAD_STATUS,
    INVALID_DRAFT_STATUS,
    VERSION_CONFLICT,
    CHAT_UNAVAILABLE,
    CONVERSATION_ACTIVE,
    AI_ANALYSIS_ACTIVE,
}

class LeadClarificationException(
    val failure: LeadClarificationFailure,
    message: String,
) : RuntimeException(message)

data class CreateReplyDraftCommand(
    val leadId: Long,
    val authorId: Long,
    val authorRole: UserRole,
    val body: String,
    val now: Instant,
)

data class UpdateReplyDraftCommand(
    val leadId: Long,
    val draftId: Long,
    val actorRole: UserRole,
    val body: String,
    val expectedVersion: Long,
    val now: Instant,
)

data class ApproveReplyDraftCommand(
    val leadId: Long,
    val draftId: Long,
    val actorRole: UserRole,
    val expectedVersion: Long,
    val now: Instant,
)

class CreateReplyDraftUseCase(
    private val leads: LeadRepository,
    private val drafts: ReplyDraftRepository,
) {
    fun create(command: CreateReplyDraftCommand): ReplyDraft {
        requireManager(command.authorRole)
        requireClarificationLead(command.leadId, leads)
        val body = normalizeBody(command.body)
        return drafts.create(ReplyDraft(
            leadId = command.leadId,
            authorId = command.authorId,
            body = body,
            createdAt = command.now,
            updatedAt = command.now,
        ))
    }
}

class UpdateReplyDraftUseCase(
    private val leads: LeadRepository,
    private val drafts: ReplyDraftRepository,
) {
    fun update(command: UpdateReplyDraftCommand): ReplyDraft {
        requireManager(command.actorRole)
        requireClarificationLead(command.leadId, leads)
        val draft = requireDraft(command.leadId, command.draftId, drafts)
        if (draft.status != ReplyDraftStatus.DRAFT) {
            fail(LeadClarificationFailure.INVALID_DRAFT_STATUS, "Only a draft can be edited")
        }
        return drafts.update(
            draft.copy(body = normalizeBody(command.body), updatedAt = command.now),
            command.expectedVersion,
        ) ?: fail(LeadClarificationFailure.VERSION_CONFLICT, "Reply draft changed; reload it and try again")
    }
}

class ApproveReplyDraftUseCase(
    private val leads: LeadRepository,
    private val drafts: ReplyDraftRepository,
) {
    fun approve(command: ApproveReplyDraftCommand): ReplyDraft {
        requireManager(command.actorRole)
        requireClarificationLead(command.leadId, leads)
        val draft = requireDraft(command.leadId, command.draftId, drafts)
        if (draft.status != ReplyDraftStatus.DRAFT) {
            fail(LeadClarificationFailure.INVALID_DRAFT_STATUS, "Only a draft can be approved")
        }
        return drafts.update(
            draft.copy(
                status = ReplyDraftStatus.APPROVED,
                updatedAt = command.now,
                approvedAt = command.now,
            ),
            command.expectedVersion,
        ) ?: fail(LeadClarificationFailure.VERSION_CONFLICT, "Reply draft changed; reload it and try again")
    }
}

class ListReplyDraftsUseCase(
    private val leads: LeadRepository,
    private val drafts: ReplyDraftRepository,
) {
    fun list(leadId: Long, reader: LeadReader): List<ReplyDraft> {
        requireManager(reader.role)
        if (leads.findById(leadId) == null) fail(LeadClarificationFailure.LEAD_NOT_FOUND, "Lead not found")
        return drafts.findByLeadId(leadId)
    }
}

class ListLeadMessagesUseCase(
    private val leads: LeadRepository,
    private val messages: LeadMessageRepository,
) {
    fun list(leadId: Long, reader: LeadReader): List<LeadMessage> {
        val lead = when (reader.role) {
            UserRole.CUSTOMER -> leads.findByIdForCustomer(leadId, reader.userId)
            UserRole.MANAGER, UserRole.ADMIN -> leads.findById(leadId)
        } ?: fail(LeadClarificationFailure.LEAD_NOT_FOUND, "Lead not found")
        val conversation = messages.findConversationByLeadId(checkNotNull(lead.id))
        return if (reader.role == UserRole.CUSTOMER) {
            conversation.filter {
                it.kind == LeadMessageKind.CUSTOMER_REPLY || it.deliveryStatus == LeadMessageDeliveryStatus.SENT
            }
        } else {
            conversation
        }
    }
}

private fun requireClarificationLead(leadId: Long, leads: LeadRepository) {
    val lead = leads.findById(leadId)
        ?: fail(LeadClarificationFailure.LEAD_NOT_FOUND, "Lead not found")
    if (lead.status != LeadStatus.CLARIFICATION) {
        fail(LeadClarificationFailure.INVALID_LEAD_STATUS, "Lead must require clarification")
    }
}

private fun requireDraft(leadId: Long, draftId: Long, drafts: ReplyDraftRepository): ReplyDraft {
    val draft = drafts.findById(draftId)
        ?: fail(LeadClarificationFailure.DRAFT_NOT_FOUND, "Reply draft not found")
    if (draft.leadId != leadId) fail(LeadClarificationFailure.DRAFT_NOT_FOUND, "Reply draft not found")
    return draft
}

private fun normalizeBody(value: String): String {
    val body = value.trim()
    if (body.isEmpty() || body.length > MAX_REPLY_LENGTH) {
        fail(LeadClarificationFailure.INVALID_BODY, "Reply must contain between 1 and $MAX_REPLY_LENGTH characters")
    }
    return body
}

private fun requireManager(role: UserRole) {
    if (role == UserRole.CUSTOMER) {
        fail(LeadClarificationFailure.FORBIDDEN, "Lead clarification is available only to managers")
    }
}

private fun fail(failure: LeadClarificationFailure, message: String): Nothing =
    throw LeadClarificationException(failure, message)

private const val MAX_REPLY_LENGTH = 2000
