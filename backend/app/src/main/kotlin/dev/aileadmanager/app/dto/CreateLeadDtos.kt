package dev.aileadmanager.app.dto

import dev.aileadmanager.core.LeadStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class CreateLeadRequest(
    @field:Positive val categoryId: Long,
    @field:NotBlank @field:Size(max = 4000) val description: String,
    @field:DecimalMin("0.0") @field:Digits(integer = 12, fraction = 2)
    val estimatedBudgetAmount: BigDecimal? = null,
    val budgetCurrency: String? = null,
    val desiredDeadline: LocalDate? = null,
    @field:NotBlank @field:Size(max = 500) val contactDetails: String,
)

data class CreateLeadResponse(
    val id: Long,
    val reference: String,
    val status: LeadStatus,
    val createdAt: Instant,
)
