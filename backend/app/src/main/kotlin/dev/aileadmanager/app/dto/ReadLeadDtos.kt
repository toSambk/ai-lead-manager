package dev.aileadmanager.app.dto

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ServiceCategory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class LeadResponse(
    val id: Long,
    val reference: String,
    val customerId: Long,
    val category: ServiceCategoryResponse,
    val description: String,
    val estimatedBudgetAmount: BigDecimal?,
    val budgetCurrency: String?,
    val desiredDeadline: LocalDate?,
    val contactDetails: String,
    val status: LeadStatus,
    val ownerId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long,
) {
    companion object {
        fun from(lead: Lead, category: ServiceCategory): LeadResponse {
            val id = checkNotNull(lead.id) { "Stored lead has no ID" }
            return LeadResponse(
                id = id,
                reference = "LM-${id.toString().padStart(6, '0')}",
                customerId = lead.customerId,
                category = ServiceCategoryResponse(
                    id = checkNotNull(category.id) { "Stored category has no ID" },
                    code = category.code,
                    name = category.name,
                ),
                description = lead.description,
                estimatedBudgetAmount = lead.estimatedBudgetAmount,
                budgetCurrency = lead.budgetCurrency,
                desiredDeadline = lead.desiredDeadline,
                contactDetails = lead.contactDetails,
                status = lead.status,
                ownerId = lead.ownerId,
                createdAt = checkNotNull(lead.createdAt) { "Stored lead has no creation time" },
                updatedAt = checkNotNull(lead.updatedAt) { "Stored lead has no update time" },
                version = checkNotNull(lead.version) { "Stored lead has no version" },
            )
        }
    }
}

data class LeadPageResponse(
    val items: List<LeadResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
