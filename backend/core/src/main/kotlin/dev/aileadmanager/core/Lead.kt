package dev.aileadmanager.core

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class Lead(
    val id: Long? = null,
    val customerId: Long,
    val categoryId: Long,
    val description: String,
    val estimatedBudgetAmount: BigDecimal? = null,
    val budgetCurrency: String? = null,
    val desiredDeadline: LocalDate? = null,
    val contactDetails: String,
    val status: LeadStatus = LeadStatus.NEW,
    val ownerId: Long? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val version: Long? = null,
)

data class LeadPage(val items: List<Lead>, val totalElements: Long)

interface LeadRepository {
    fun save(lead: Lead): Lead
    fun findById(id: Long): Lead?
    fun findByIdForCustomer(id: Long, customerId: Long): Lead?
    fun findByCustomerId(customerId: Long, page: Int, size: Int): LeadPage
    fun findAll(page: Int, size: Int): LeadPage
}
