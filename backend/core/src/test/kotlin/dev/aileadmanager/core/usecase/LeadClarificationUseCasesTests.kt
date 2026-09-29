package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.LeadMessageDeliveryStatus
import dev.aileadmanager.core.LeadMessageRepository
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ReplyDraft
import dev.aileadmanager.core.ReplyDraftRepository
import dev.aileadmanager.core.ReplyDraftStatus
import dev.aileadmanager.core.UserRole
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LeadClarificationUseCasesTests {
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    @Test
    fun `manager creates edits and approves a clarification draft`() {
        val leads = FakeLeadRepository(Lead(id = 10, customerId = 3, categoryId = 1, description = "Bot", contactDetails = "@c", status = LeadStatus.CLARIFICATION))
        val drafts = FakeReplyDraftRepository()
        val created = CreateReplyDraftUseCase(leads, drafts).create(CreateReplyDraftCommand(
            leadId = 10,
            authorId = 7,
            authorRole = UserRole.MANAGER,
            body = "  What is your deadline?  ",
            now = now,
        ))
        assertEquals("What is your deadline?", created.body)

        val updated = UpdateReplyDraftUseCase(leads, drafts).update(UpdateReplyDraftCommand(
            leadId = 10,
            draftId = checkNotNull(created.id),
            actorRole = UserRole.MANAGER,
            body = "What budget and deadline do you expect?",
            expectedVersion = 0,
            now = now.plusSeconds(1),
        ))
        assertEquals(1, updated.version)

        val approved = ApproveReplyDraftUseCase(leads, drafts).approve(ApproveReplyDraftCommand(
            leadId = 10,
            draftId = checkNotNull(updated.id),
            actorRole = UserRole.ADMIN,
            expectedVersion = 1,
            now = now.plusSeconds(2),
        ))
        assertEquals(ReplyDraftStatus.APPROVED, approved.status)
        assertEquals(2, approved.version)
    }

    @Test
    fun `draft cannot be created outside clarification`() {
        val leads = FakeLeadRepository(Lead(id = 10, customerId = 3, categoryId = 1, description = "Bot", contactDetails = "@c"))
        val error = assertFailsWith<LeadClarificationException> {
            CreateReplyDraftUseCase(leads, FakeReplyDraftRepository()).create(CreateReplyDraftCommand(
                10, 7, UserRole.MANAGER, "Question", now,
            ))
        }
        assertEquals(LeadClarificationFailure.INVALID_LEAD_STATUS, error.failure)
    }

    @Test
    fun `customer reads only conversation messages from own lead`() {
        val lead = Lead(id = 10, customerId = 3, categoryId = 1, description = "Bot", contactDetails = "@c")
        val messages = FakeLeadMessageRepository(listOf(
            LeadMessage(id = 1, leadId = 10, senderId = 7, kind = LeadMessageKind.FOLLOW_UP_QUESTION, body = "Deadline?", deliveryStatus = LeadMessageDeliveryStatus.SENT, createdAt = now),
            LeadMessage(id = 2, leadId = 10, senderId = 3, kind = LeadMessageKind.CUSTOMER_REPLY, body = "2026-12-01", createdAt = now),
        ))
        val result = ListLeadMessagesUseCase(FakeLeadRepository(lead), messages)
            .list(10, LeadReader(3, UserRole.CUSTOMER))
        assertEquals(2, result.size)

        val error = assertFailsWith<LeadClarificationException> {
            ListLeadMessagesUseCase(FakeLeadRepository(lead), messages)
                .list(10, LeadReader(4, UserRole.CUSTOMER))
        }
        assertEquals(LeadClarificationFailure.LEAD_NOT_FOUND, error.failure)
    }
}

private class FakeLeadRepository(private val lead: Lead) : LeadRepository {
    override fun save(lead: Lead) = lead
    override fun findById(id: Long) = lead.takeIf { it.id == id }
    override fun findByIdForCustomer(id: Long, customerId: Long) = lead.takeIf { it.id == id && it.customerId == customerId }
    override fun findByCustomerId(customerId: Long, page: Int, size: Int) = LeadPage(emptyList(), 0)
    override fun findAll(page: Int, size: Int) = LeadPage(emptyList(), 0)
}

private class FakeReplyDraftRepository : ReplyDraftRepository {
    private val values = mutableMapOf<Long, ReplyDraft>()
    private var nextId = 1L

    override fun create(draft: ReplyDraft): ReplyDraft = draft.copy(id = nextId++).also { values[checkNotNull(it.id)] = it }
    override fun update(draft: ReplyDraft, expectedVersion: Long): ReplyDraft? {
        val current = values[draft.id] ?: return null
        if (current.version != expectedVersion) return null
        return draft.copy(version = expectedVersion + 1).also { values[checkNotNull(it.id)] = it }
    }
    override fun findById(id: Long) = values[id]
    override fun findByLeadId(leadId: Long) = values.values.filter { it.leadId == leadId }
    override fun markSent(id: Long, sentAt: Instant) = Unit
    override fun markFailed(id: Long, updatedAt: Instant) = Unit
}

private class FakeLeadMessageRepository(private val conversation: List<LeadMessage>) : LeadMessageRepository {
    override fun save(message: LeadMessage) = message
    override fun findInternalNotesByLeadId(leadId: Long) = emptyList<LeadMessage>()
    override fun findConversationByLeadId(leadId: Long) = conversation.filter { it.leadId == leadId }
    override fun markDeliverySucceeded(messageId: Long, telegramChatId: Long, telegramMessageId: Long) = Unit
    override fun markDeliveryFailed(messageId: Long) = Unit
}
