package dev.aileadmanager.ai

import dev.aileadmanager.core.AiAnalysisDraft
import dev.aileadmanager.core.AiAnalysisInput
import dev.aileadmanager.core.AiExtractedFacts
import dev.aileadmanager.core.AiMissingField
import dev.aileadmanager.core.AiPriority
import dev.aileadmanager.core.AiProvider
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StubAiProvider : AiProvider {
    override fun analyze(input: AiAnalysisInput): AiAnalysisDraft {
        val lead = input.lead
        val missingFields = buildSet {
            if (lead.estimatedBudgetAmount == null) add(AiMissingField.BUDGET)
            if (lead.desiredDeadline == null) add(AiMissingField.DEADLINE)
        }
        val daysUntilDeadline = lead.desiredDeadline?.let { ChronoUnit.DAYS.between(LocalDate.now(), it) }
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
            summary = "${input.category.name}: ${lead.description.take(400)}",
            extractedFacts = AiExtractedFacts(
                category = input.category.code,
                budgetAmount = lead.estimatedBudgetAmount?.toPlainString(),
                budgetCurrency = lead.budgetCurrency,
                desiredDeadline = lead.desiredDeadline?.toString(),
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
}
