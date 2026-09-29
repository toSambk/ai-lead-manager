package dev.aileadmanager.app.dto

import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageDeliveryStatus
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.ReplyDraft
import dev.aileadmanager.core.ReplyDraftStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateReplyDraftRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val body: String,
)

data class UpdateReplyDraftRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val body: String,
    @field:PositiveOrZero
    val version: Long,
)

data class SendReplyDraftRequest(
    @field:PositiveOrZero
    val version: Long,
)

data class ReplyDraftResponse(
    val id: Long,
    val leadId: Long,
    val authorId: Long,
    val body: String,
    val status: ReplyDraftStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val approvedAt: Instant?,
    val sentAt: Instant?,
) {
    companion object {
        fun from(draft: ReplyDraft) = ReplyDraftResponse(
            id = checkNotNull(draft.id),
            leadId = draft.leadId,
            authorId = draft.authorId,
            body = draft.body,
            status = draft.status,
            version = draft.version,
            createdAt = checkNotNull(draft.createdAt),
            updatedAt = checkNotNull(draft.updatedAt),
            approvedAt = draft.approvedAt,
            sentAt = draft.sentAt,
        )
    }
}

data class LeadMessageResponse(
    val id: Long,
    val senderId: Long?,
    val kind: LeadMessageKind,
    val body: String,
    val deliveryStatus: LeadMessageDeliveryStatus?,
    val createdAt: Instant,
) {
    companion object {
        fun from(message: LeadMessage) = LeadMessageResponse(
            id = checkNotNull(message.id),
            senderId = message.senderId,
            kind = message.kind,
            body = message.body,
            deliveryStatus = message.deliveryStatus,
            createdAt = checkNotNull(message.createdAt),
        )
    }
}
