package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReadLeadUseCasesTests {
    private val customerLead = Lead(id = 10, customerId = 7, categoryId = 3, description = "Customer lead", contactDetails = "@customer")
    private val otherLead = Lead(id = 11, customerId = 8, categoryId = 3, description = "Other lead", contactDetails = "@other")
    private val repository = FakeLeads(listOf(customerLead, otherLead))

    @Test
    fun `customer reads only an owned lead`() {
        val useCase = GetLeadUseCase(repository)
        val reader = LeadReader(7, UserRole.CUSTOMER)

        assertEquals(customerLead, useCase.get(10, reader))
        assertNull(useCase.get(11, reader))
    }

    @Test
    fun `manager reads any lead`() {
        val result = GetLeadUseCase(repository).get(11, LeadReader(20, UserRole.MANAGER))

        assertEquals(otherLead, result)
    }

    @Test
    fun `customer list is scoped by customer id`() {
        val result = ListLeadsUseCase(repository).list(LeadReader(7, UserRole.CUSTOMER), 0, 20)

        assertEquals(listOf(customerLead), result.items)
        assertEquals(1, result.totalElements)
    }

    @Test
    fun `manager list contains all leads`() {
        val result = ListLeadsUseCase(repository).list(LeadReader(20, UserRole.MANAGER), 0, 20)

        assertEquals(listOf(customerLead, otherLead), result.items)
        assertEquals(2, result.totalElements)
    }

    @Test
    fun `list rejects invalid pagination`() {
        val useCase = ListLeadsUseCase(repository)
        val reader = LeadReader(7, UserRole.CUSTOMER)

        assertFailsWith<IllegalArgumentException> { useCase.list(reader, -1, 20) }
        assertFailsWith<IllegalArgumentException> { useCase.list(reader, 0, 101) }
    }

    private class FakeLeads(private val leads: List<Lead>) : LeadRepository {
        override fun save(lead: Lead) = lead
        override fun findById(id: Long) = leads.find { it.id == id }
        override fun findByIdForCustomer(id: Long, customerId: Long) =
            leads.find { it.id == id && it.customerId == customerId }

        override fun findByCustomerId(customerId: Long, page: Int, size: Int): LeadPage =
            leads.filter { it.customerId == customerId }.toPage(page, size)

        override fun findAll(page: Int, size: Int): LeadPage = leads.toPage(page, size)

        private fun List<Lead>.toPage(page: Int, size: Int): LeadPage {
            val fromIndex = (page * size).coerceAtMost(count())
            val toIndex = (fromIndex + size).coerceAtMost(count())
            return LeadPage(subList(fromIndex, toIndex), count().toLong())
        }
    }
}
