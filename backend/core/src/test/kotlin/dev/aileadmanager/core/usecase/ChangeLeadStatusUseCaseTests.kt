package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChangeLeadStatusUseCaseTests {
    private val leads = FakeLeads()
    private val events = FakeEvents()
    private val useCase = ChangeLeadStatusUseCase(leads, events)

    @Test
    fun `manager changes status and records an event`() {
        val result = useCase.change(command(status = LeadStatus.IN_PROGRESS))

        assertEquals(LeadStatus.IN_PROGRESS, result.lead.status)
        assertEquals(1L, result.lead.version)
        assertEquals(LeadEventType.STATUS_CHANGED, result.event.type)
        assertEquals(LeadStatus.NEW, result.event.oldStatus)
        assertEquals(LeadStatus.IN_PROGRESS, result.event.newStatus)
        assertEquals(20L, result.event.actorId)
    }

    @Test
    fun `customer cannot change status`() {
        val error = assertFailsWith<ChangeLeadStatusException> {
            useCase.change(command(actorRole = UserRole.CUSTOMER))
        }

        assertEquals(ChangeLeadStatusFailure.FORBIDDEN, error.failure)
        assertNull(events.saved)
    }

    @Test
    fun `invalid transition does not update the lead or audit`() {
        leads.lead = leads.lead.copy(status = LeadStatus.COMPLETED)

        val error = assertFailsWith<ChangeLeadStatusException> {
            useCase.change(command(status = LeadStatus.IN_PROGRESS))
        }

        assertEquals(ChangeLeadStatusFailure.INVALID_TRANSITION, error.failure)
        assertEquals(LeadStatus.COMPLETED, leads.lead.status)
        assertNull(events.saved)
    }

    @Test
    fun `stale version does not update the lead or audit`() {
        val error = assertFailsWith<ChangeLeadStatusException> {
            useCase.change(command(expectedVersion = 4))
        }

        assertEquals(ChangeLeadStatusFailure.VERSION_CONFLICT, error.failure)
        assertEquals(LeadStatus.NEW, leads.lead.status)
        assertNull(events.saved)
    }

    private fun command(
        actorRole: UserRole = UserRole.MANAGER,
        status: LeadStatus = LeadStatus.CLARIFICATION,
        expectedVersion: Long = 0,
    ) = ChangeLeadStatusCommand(
        leadId = 10,
        actorId = 20,
        actorRole = actorRole,
        status = status,
        expectedVersion = expectedVersion,
    )

    private class FakeLeads : LeadRepository {
        var lead = Lead(
            id = 10,
            customerId = 7,
            categoryId = 3,
            description = "Build a website",
            contactDetails = "@customer",
            version = 0,
        )

        override fun save(lead: Lead): Lead = lead.copy(version = checkNotNull(lead.version) + 1).also { this.lead = it }
        override fun findById(id: Long) = lead.takeIf { it.id == id }
        override fun findByIdForCustomer(id: Long, customerId: Long) = lead.takeIf { it.id == id && it.customerId == customerId }
        override fun findByCustomerId(customerId: Long, page: Int, size: Int) = LeadPage(emptyList(), 0)
        override fun findAll(page: Int, size: Int) = LeadPage(listOf(lead), 1)
    }

    private class FakeEvents : LeadEventRepository {
        var saved: LeadEvent? = null
        override fun save(event: LeadEvent) = event.copy(id = 30).also { saved = it }
        override fun findByLeadId(leadId: Long) = listOfNotNull(saved).filter { it.leadId == leadId }
    }
}
