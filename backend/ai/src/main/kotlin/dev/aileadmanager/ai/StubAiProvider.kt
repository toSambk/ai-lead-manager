package dev.aileadmanager.ai

import dev.aileadmanager.core.AiAnalysisDraft
import dev.aileadmanager.core.AiAnalysisInput
import dev.aileadmanager.core.AiExtractedFacts
import dev.aileadmanager.core.AiMissingField
import dev.aileadmanager.core.AiPriority
import dev.aileadmanager.core.AiProvider
import dev.aileadmanager.core.LeadMessageKind
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StubAiProvider : AiProvider {
    override fun analyze(input: AiAnalysisInput): AiAnalysisDraft {
        val lead = input.lead
        val customerText = input.conversation
            .filter { it.kind == LeadMessageKind.CUSTOMER_REPLY }
            .joinToString("\n") { it.body }
        val extractedBudget = extractBudget(customerText)
        val extractedDeadline = extractDeadline(customerText)
        val budgetAmount = lead.estimatedBudgetAmount?.toPlainString() ?: extractedBudget?.first
        val budgetCurrency = lead.budgetCurrency ?: extractedBudget?.second
        val deadline = lead.desiredDeadline?.toString() ?: extractedDeadline
        val missingFields = buildSet {
            if (budgetAmount == null) add(AiMissingField.BUDGET)
            if (deadline == null) add(AiMissingField.DEADLINE)
        }
        val daysUntilDeadline = deadline?.let(LocalDate::parse)?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
        val priority = when {
            daysUntilDeadline != null && daysUntilDeadline <= 14 -> AiPriority.HIGH
            daysUntilDeadline != null && daysUntilDeadline <= 45 -> AiPriority.MEDIUM
            else -> AiPriority.LOW
        }
        val question = when {
            AiMissingField.BUDGET in missingFields -> "What budget range are you considering?"
            AiMissingField.DEADLINE in missingFields -> "When would you like the work to be completed?"
            else -> null
        }
        return AiAnalysisDraft(
            summary = buildString {
                append("${input.category.name}: ${lead.description.take(400)}")
                input.conversation.lastOrNull { it.kind == LeadMessageKind.CUSTOMER_REPLY }?.let {
                    append("\n\nCustomer clarification: ${it.body.take(250)}")
                }
            },
            extractedFacts = AiExtractedFacts(
                category = input.category.code,
                budgetAmount = budgetAmount,
                budgetCurrency = budgetCurrency,
                desiredDeadline = deadline,
            ),
            missingFields = missingFields,
            suggestedQuestion = question,
            priority = priority,
            priorityReason = when (priority) {
                AiPriority.HIGH -> "The requested deadline is within two weeks."
                AiPriority.MEDIUM -> "The requested deadline is within 45 days."
                AiPriority.LOW -> "No urgent deadline was identified."
            },
        )
    }

    private fun extractBudget(text: String): Pair<String, String>? {
        val match = BUDGET_PATTERN.find(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return null
        if (amount.signum() < 0 || amount.scale() > 2 || amount.precision() > 14) return null
        return amount.setScale(amount.scale().coerceAtLeast(0)).toPlainString() to match.groupValues[2].uppercase()
    }

    private fun extractDeadline(text: String): String? = DATE_PATTERN.findAll(text)
        .map { it.value }
        .firstOrNull { runCatching { LocalDate.parse(it) }.isSuccess }

    private companion object {
        val BUDGET_PATTERN = Regex("""(?i)\b(\d+(?:[.,]\d{1,2})?)\s*(USD|EUR|RUB)\b""")
        val DATE_PATTERN = Regex("""\b\d{4}-\d{2}-\d{2}\b""")
    }
}
