package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.AiJobRepository
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.UserRole
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.util.Currency
import java.util.Locale

data class CreateLeadCommand(
    val customerId: Long,
    val categoryId: Long,
    val description: String,
    val estimatedBudgetAmount: BigDecimal? = null,
    val budgetCurrency: String? = null,
    val desiredDeadline: LocalDate? = null,
    val contactDetails: String,
)

enum class CreateLeadFailure { INVALID_INPUT, CUSTOMER_UNAVAILABLE, CATEGORY_UNAVAILABLE }

class CreateLeadException(val failure: CreateLeadFailure, message: String) : RuntimeException(message)

class CreateLeadUseCase(
    private val users: UserRepository,
    private val categories: ServiceCategoryRepository,
    private val leads: LeadRepository,
    private val aiJobs: AiJobRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(command: CreateLeadCommand): Lead {
        val customer = users.findById(command.customerId)
        if (customer?.role != UserRole.CUSTOMER) {
            throw CreateLeadException(CreateLeadFailure.CUSTOMER_UNAVAILABLE, "Customer identity is unavailable")
        }

        val category = categories.findById(command.categoryId)
        if (category?.active != true) {
            throw CreateLeadException(CreateLeadFailure.CATEGORY_UNAVAILABLE, "Service category is unavailable")
        }

        val description = command.description.trim()
        val contactDetails = command.contactDetails.trim()
        if (description.isEmpty() || description.length > 4000 || contactDetails.isEmpty() || contactDetails.length > 500) {
            invalid("Description and contact details must be present and within the allowed lengths")
        }

        val amount = command.estimatedBudgetAmount
        val currency = command.budgetCurrency?.trim()?.uppercase(Locale.ROOT)
        if ((amount == null) != (currency == null)) {
            invalid("Budget amount and currency must be provided together")
        }
        if (amount != null && (amount.signum() < 0 || amount.scale() > 2 || amount > MAX_BUDGET)) {
            invalid("Budget amount must be nonnegative with at most two decimal places")
        }
        if (currency != null && (!CURRENCY_CODE.matches(currency) || !isKnownCurrency(currency))) {
            invalid("Budget currency must be an ISO 4217 code")
        }

        val lead = leads.save(Lead(
            customerId = customer.id ?: throw CreateLeadException(
                CreateLeadFailure.CUSTOMER_UNAVAILABLE, "Customer identity is unavailable",
            ),
            categoryId = category.id ?: throw CreateLeadException(
                CreateLeadFailure.CATEGORY_UNAVAILABLE, "Service category is unavailable",
            ),
            description = description,
            estimatedBudgetAmount = amount,
            budgetCurrency = currency,
            desiredDeadline = command.desiredDeadline,
            contactDetails = contactDetails,
        ))
        aiJobs.enqueue(
            leadId = checkNotNull(lead.id) { "Saved lead has no ID" },
            now = Instant.now(clock),
        )
        return lead
    }

    private fun isKnownCurrency(code: String): Boolean = try {
        Currency.getInstance(code) != null
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun invalid(message: String): Nothing =
        throw CreateLeadException(CreateLeadFailure.INVALID_INPUT, message)

    private companion object {
        val MAX_BUDGET = BigDecimal("999999999999.99")
        val CURRENCY_CODE = Regex("[A-Z]{3}")
    }
}
