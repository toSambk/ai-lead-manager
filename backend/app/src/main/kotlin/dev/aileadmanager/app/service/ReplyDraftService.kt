package dev.aileadmanager.app.service

import dev.aileadmanager.core.ReplyDraft
import dev.aileadmanager.core.UserRole
import dev.aileadmanager.core.usecase.CreateReplyDraftCommand
import dev.aileadmanager.core.usecase.CreateReplyDraftUseCase
import dev.aileadmanager.core.usecase.UpdateReplyDraftCommand
import dev.aileadmanager.core.usecase.UpdateReplyDraftUseCase
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReplyDraftService(
    private val createDraft: CreateReplyDraftUseCase,
    private val updateDraft: UpdateReplyDraftUseCase,
    private val clock: Clock,
) {
    @Transactional
    fun create(leadId: Long, authorId: Long, role: UserRole, body: String): ReplyDraft =
        createDraft.create(CreateReplyDraftCommand(leadId, authorId, role, body, Instant.now(clock)))

    @Transactional
    fun update(
        leadId: Long,
        draftId: Long,
        role: UserRole,
        body: String,
        version: Long,
    ): ReplyDraft = updateDraft.update(UpdateReplyDraftCommand(
        leadId,
        draftId,
        role,
        body,
        version,
        Instant.now(clock),
    ))
}
