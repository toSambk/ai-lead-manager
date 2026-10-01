package dev.aileadmanager.app.service.lead

import dev.aileadmanager.core.AiJobRepository
import dev.aileadmanager.core.AiJobStatus
import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageDeliveryStatus
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.LeadMessageRepository
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ReplyDraft
import dev.aileadmanager.core.TelegramChatBindingRepository
import dev.aileadmanager.core.TelegramConversation
import dev.aileadmanager.core.TelegramConversationRepository
import dev.aileadmanager.core.TelegramDeliveryJob
import dev.aileadmanager.core.TelegramDeliveryJobRepository
import dev.aileadmanager.core.TelegramDeliveryType
import dev.aileadmanager.core.UserRole
import dev.aileadmanager.core.usecase.ApproveReplyDraftCommand
import dev.aileadmanager.core.usecase.ApproveReplyDraftUseCase
import dev.aileadmanager.core.usecase.LeadClarificationException
import dev.aileadmanager.core.usecase.LeadClarificationFailure
import java.time.Clock
import java.time.Instant
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeadClarificationService(
    private val approveDraft: ApproveReplyDraftUseCase,
    private val leads: LeadRepository,
    private val messages: LeadMessageRepository,
    private val chats: TelegramChatBindingRepository,
    private val conversations: TelegramConversationRepository,
    private val deliveries: TelegramDeliveryJobRepository,
    private val aiJobs: AiJobRepository,
    private val clock: Clock,
) {
    @Transactional
    fun approveAndSend(
        leadId: Long,
        draftId: Long,
        actorRole: UserRole,
        expectedVersion: Long,
    ): ReplyDraft {
        val now = Instant.now(clock)
        val lead = leads.findById(leadId)
            ?: fail(LeadClarificationFailure.LEAD_NOT_FOUND, "Lead not found")
        if (aiJobs.findLatestByLeadId(leadId)?.status in setOf(AiJobStatus.PENDING, AiJobStatus.RUNNING)) {
            fail(LeadClarificationFailure.AI_ANALYSIS_ACTIVE, "Wait for the active AI analysis before sending a question")
        }
        val chat = chats.findPrivateChatByUserId(lead.customerId)
            ?: fail(LeadClarificationFailure.CHAT_UNAVAILABLE, "Customer has not opened a private bot chat")
        val approved = approveDraft.approve(ApproveReplyDraftCommand(
            leadId = leadId,
            draftId = draftId,
            actorRole = actorRole,
            expectedVersion = expectedVersion,
            now = now,
        ))
        val question = messages.save(LeadMessage(
            leadId = leadId,
            senderId = approved.authorId,
            kind = LeadMessageKind.FOLLOW_UP_QUESTION,
            body = approved.body,
            deliveryStatus = LeadMessageDeliveryStatus.PENDING,
        ))
        val conversation = try {
            conversations.create(TelegramConversation(
                customerId = lead.customerId,
                leadId = leadId,
                questionMessageId = checkNotNull(question.id),
                createdAt = now,
                updatedAt = now,
            ))
        } catch (error: DataIntegrityViolationException) {
            throw LeadClarificationException(
                LeadClarificationFailure.CONVERSATION_ACTIVE,
                "Customer already has an active clarification conversation",
            )
        }
        deliveries.enqueue(TelegramDeliveryJob(
            messageKey = "REPLY_DRAFT:${checkNotNull(approved.id)}",
            type = TelegramDeliveryType.MANAGER_APPROVED_REPLY,
            chatId = chat.telegramChatId,
            leadId = leadId,
            replyDraftId = approved.id,
            leadMessageId = question.id,
            conversationId = conversation.id,
            text = approved.body,
            nextAttemptAt = now,
        ))
        return approved
    }

    @Transactional
    fun recordCustomerReply(
        customerId: Long,
        telegramChatId: Long,
        telegramMessageId: Long,
        text: String,
    ): Boolean {
        val conversation = conversations.findAwaitingReplyByCustomerId(customerId) ?: return false
        val lead = leads.findById(conversation.leadId)
            ?: error("Lead ${conversation.leadId} disappeared during clarification")
        if (lead.status != LeadStatus.CLARIFICATION) {
            conversations.markCancelled(checkNotNull(conversation.id), Instant.now(clock))
            return false
        }
        val body = text.trim()
        if (body.isEmpty()) return false
        val now = Instant.now(clock)
        messages.save(LeadMessage(
            leadId = conversation.leadId,
            senderId = customerId,
            kind = LeadMessageKind.CUSTOMER_REPLY,
            body = body.take(MAX_REPLY_LENGTH),
            telegramChatId = telegramChatId,
            telegramMessageId = telegramMessageId,
        ))
        conversations.markAnswered(checkNotNull(conversation.id), now)
        aiJobs.enqueue(conversation.leadId, now)
        return true
    }

    private fun fail(failure: LeadClarificationFailure, message: String): Nothing =
        throw LeadClarificationException(failure, message)

    private companion object {
        const val MAX_REPLY_LENGTH = 2000
    }
}
