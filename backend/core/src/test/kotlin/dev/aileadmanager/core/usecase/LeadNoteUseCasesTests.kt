package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.LeadMessageRepository
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LeadNoteUseCasesTests {
    private val leads = FakeLeads()
    private val messages = FakeMessages()
    private val addUseCase = AddLeadNoteUseCase(leads, messages)
    private val listUseCase = ListLeadNotesUseCase(leads, messages)

    @Test
    fun `manager adds a trimmed internal note`() {
        val note = addUseCase.add(AddLeadNoteCommand(5, 20, UserRole.MANAGER, "  Call after 4 PM  "))

        assertEquals(LeadMessageKind.INTERNAL_NOTE, note.kind)
        assertEquals("Call after 4 PM", note.body)
        assertEquals(20L, note.senderId)
        assertEquals(listOf(note), listUseCase.list(5, LeadReader(20, UserRole.MANAGER)))
    }

    @Test
    fun `customer cannot add or read internal notes`() {
        val addError = assertFailsWith<LeadNoteException> {
            addUseCase.add(AddLeadNoteCommand(5, 10, UserRole.CUSTOMER, "Hidden note"))
        }
        val listError = assertFailsWith<LeadNoteException> {
            listUseCase.list(5, LeadReader(10, UserRole.CUSTOMER))
        }

        assertEquals(LeadNoteFailure.FORBIDDEN, addError.failure)
        assertEquals(LeadNoteFailure.FORBIDDEN, listError.failure)
        assertNull(messages.saved)
    }

    @Test
    fun `blank or oversized note is rejected`() {
        val blankError = assertFailsWith<LeadNoteException> {
            addUseCase.add(AddLeadNoteCommand(5, 20, UserRole.MANAGER, "   "))
        }
        val longError = assertFailsWith<LeadNoteException> {
            addUseCase.add(AddLeadNoteCommand(5, 20, UserRole.MANAGER, "x".repeat(2001)))
        }

        assertEquals(LeadNoteFailure.INVALID_BODY, blankError.failure)
        assertEquals(LeadNoteFailure.INVALID_BODY, longError.failure)
        assertNull(messages.saved)
    }

    @Test
    fun `missing lead is rejected`() {
        val error = assertFailsWith<LeadNoteException> {
            addUseCase.add(AddLeadNoteCommand(404, 20, UserRole.MANAGER, "Check details"))
        }

        assertEquals(LeadNoteFailure.LEAD_NOT_FOUND, error.failure)
        assertNull(messages.saved)
    }

    private class FakeLeads : LeadRepository {
        private val lead = Lead(
            id = 5,
            customerId = 10,
            categoryId = 3,
            description = "Build a website",
            contactDetails = "@customer",
            version = 0,
        )

        override fun save(lead: Lead) = lead
        override fun findById(id: Long) = lead.takeIf { it.id == id }
        override fun findByIdForCustomer(id: Long, customerId: Long) = lead.takeIf { it.id == id && it.customerId == customerId }
        override fun findByCustomerId(customerId: Long, page: Int, size: Int) = LeadPage(emptyList(), 0)
        override fun findAll(page: Int, size: Int) = LeadPage(listOf(lead), 1)
    }

    private class FakeMessages : LeadMessageRepository {
        var saved: LeadMessage? = null

        override fun save(message: LeadMessage) = message.copy(id = 30).also { saved = it }
        override fun findInternalNotesByLeadId(leadId: Long) = listOfNotNull(saved).filter { it.leadId == leadId }
    }
}
