package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssignLeadOwnerUseCaseTests {
    private val leads = FakeLeads()
    private val users = FakeUsers()
    private val events = FakeEvents()
    private val useCase = AssignLeadOwnerUseCase(leads, users, events)

    @Test
    fun `manager assigns an eligible owner and records an event`() {
        val result = useCase.assign(command(ownerId = 20))

        assertEquals(20L, result.lead.ownerId)
        assertEquals(1L, result.lead.version)
        assertEquals(LeadEventType.OWNER_CHANGED, result.event.type)
        assertNull(result.event.oldOwnerId)
        assertEquals(20L, result.event.newOwnerId)
        assertEquals(30L, result.event.actorId)
    }

    @Test
    fun `manager can unassign the current owner`() {
        leads.lead = leads.lead.copy(ownerId = 20)

        val result = useCase.assign(command(ownerId = null))

        assertNull(result.lead.ownerId)
        assertEquals(20L, result.event.oldOwnerId)
        assertNull(result.event.newOwnerId)
    }

    @Test
    fun `customer cannot assign an owner`() {
        val error = assertFailsWith<AssignLeadOwnerException> {
            useCase.assign(command(ownerId = 20, actorRole = UserRole.CUSTOMER))
        }

        assertEquals(AssignLeadOwnerFailure.FORBIDDEN, error.failure)
        assertNull(events.saved)
    }

    @Test
    fun `customer cannot be selected as an owner`() {
        val error = assertFailsWith<AssignLeadOwnerException> {
            useCase.assign(command(ownerId = 10))
        }

        assertEquals(AssignLeadOwnerFailure.OWNER_UNAVAILABLE, error.failure)
        assertNull(leads.lead.ownerId)
        assertNull(events.saved)
    }

    @Test
    fun `unchanged owner does not update the lead or audit`() {
        leads.lead = leads.lead.copy(ownerId = 20)

        val error = assertFailsWith<AssignLeadOwnerException> {
            useCase.assign(command(ownerId = 20))
        }

        assertEquals(AssignLeadOwnerFailure.OWNER_UNCHANGED, error.failure)
        assertEquals(0L, leads.lead.version)
        assertNull(events.saved)
    }

    @Test
    fun `stale version does not update the lead or audit`() {
        val error = assertFailsWith<AssignLeadOwnerException> {
            useCase.assign(command(ownerId = 20, expectedVersion = 2))
        }

        assertEquals(AssignLeadOwnerFailure.VERSION_CONFLICT, error.failure)
        assertNull(leads.lead.ownerId)
        assertNull(events.saved)
    }

    private fun command(
        ownerId: Long?,
        actorRole: UserRole = UserRole.MANAGER,
        expectedVersion: Long = 0,
    ) = AssignLeadOwnerCommand(
        leadId = 5,
        actorId = 30,
        actorRole = actorRole,
        ownerId = ownerId,
        expectedVersion = expectedVersion,
    )

    private class FakeLeads : LeadRepository {
        var lead = Lead(
            id = 5,
            customerId = 10,
            categoryId = 3,
            description = "Build a website",
            contactDetails = "@customer",
            version = 0,
        )

        override fun save(lead: Lead) = lead.copy(version = checkNotNull(lead.version) + 1).also { this.lead = it }
        override fun findById(id: Long) = lead.takeIf { it.id == id }
        override fun findByIdForCustomer(id: Long, customerId: Long) = lead.takeIf { it.id == id && it.customerId == customerId }
        override fun findByCustomerId(customerId: Long, page: Int, size: Int) = LeadPage(emptyList(), 0)
        override fun findAll(page: Int, size: Int) = LeadPage(listOf(lead), 1)
    }

    private class FakeUsers : UserRepository {
        private val users = listOf(
            User(id = 10, telegramUserId = 100, displayName = "Customer"),
            User(id = 20, telegramUserId = 200, displayName = "Manager", role = UserRole.MANAGER),
            User(id = 21, telegramUserId = 210, displayName = "Administrator", role = UserRole.ADMIN),
        )

        override fun save(user: User) = user
        override fun upsertTelegramProfile(telegramUserId: Long, displayName: String) = users.first()
        override fun findById(id: Long) = users.find { it.id == id }
        override fun findByTelegramUserId(telegramUserId: Long) = users.find { it.telegramUserId == telegramUserId }
        override fun findAssignableManagers() = users.filter { it.role != UserRole.CUSTOMER }
    }

    private class FakeEvents : LeadEventRepository {
        var saved: LeadEvent? = null
        override fun save(event: LeadEvent) = event.copy(id = 40).also { saved = it }
        override fun findByLeadId(leadId: Long) = listOfNotNull(saved).filter { it.leadId == leadId }
    }
}
