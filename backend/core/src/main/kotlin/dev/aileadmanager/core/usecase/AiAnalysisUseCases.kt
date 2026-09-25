package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.AiAnalysisDraft
import dev.aileadmanager.core.AiAnalysisResult
import dev.aileadmanager.core.AiAnalysisResultRepository
import dev.aileadmanager.core.AiJob
import dev.aileadmanager.core.AiJobRepository
import dev.aileadmanager.core.AiJobStatus
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.UserRole
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class InvalidAiAnalysis(message: String) : RuntimeException(message)

class AiAnalysisValidator {
    fun validate(job: AiJob, draft: AiAnalysisDraft): AiAnalysisResult {
        val jobId = requireNotNull(job.id) { "Stored AI job has no ID" }
        val summary = requiredText(draft.summary, 1000, "summary")
        val priorityReason = requiredText(draft.priorityReason, 500, "priority reason")
        val question = draft.suggestedQuestion?.trim()?.takeIf(String::isNotEmpty)
        if (draft.missingFields.isNotEmpty() && question == null) {
            throw InvalidAiAnalysis("A clarification question is required when information is missing")
        }
        if (question != null && question.length > 1000) {
            throw InvalidAiAnalysis("Suggested question is too long")
        }
        val category = requiredText(draft.extractedFacts.category, 100, "category")
        val budgetAmount = draft.extractedFacts.budgetAmount?.trim()?.takeIf(String::isNotEmpty)
        val budgetCurrency = draft.extractedFacts.budgetCurrency?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
        if ((budgetAmount == null) != (budgetCurrency == null)) {
            throw InvalidAiAnalysis("Extracted budget amount and currency must be provided together")
        }
        if (budgetAmount != null) {
            val parsed = budgetAmount.toBigDecimalOrNull()
            if (parsed == null || parsed.signum() < 0 || parsed.scale() > 2 || parsed.precision() > 14) {
                throw InvalidAiAnalysis("Extracted budget amount is invalid")
            }
            if (!Regex("[A-Z]{3}").matches(checkNotNull(budgetCurrency))) {
                throw InvalidAiAnalysis("Extracted budget currency is invalid")
            }
        }
        val deadline = draft.extractedFacts.desiredDeadline?.trim()?.takeIf(String::isNotEmpty)
        if (deadline != null) {
            runCatching { LocalDate.parse(deadline) }
                .getOrElse { throw InvalidAiAnalysis("Extracted deadline is invalid") }
        }
        return AiAnalysisResult(
            jobId = jobId,
            leadId = job.leadId,
            summary = summary,
            extractedFacts = draft.extractedFacts.copy(
                category = category,
                budgetAmount = budgetAmount,
                budgetCurrency = budgetCurrency,
                desiredDeadline = deadline,
            ),
            missingFields = draft.missingFields,
            suggestedQuestion = question,
            priority = draft.priority,
            priorityReason = priorityReason,
        )
    }

    private fun requiredText(value: String, maxLength: Int, field: String): String {
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.length > maxLength) {
            throw InvalidAiAnalysis("AI $field must contain between 1 and $maxLength characters")
        }
        return normalized
    }
}

data class LeadAiAnalysisView(
    val job: AiJob,
    val result: AiAnalysisResult?,
)

enum class AiAnalysisFailure { LEAD_NOT_FOUND, FORBIDDEN, ANALYSIS_NOT_FOUND, RETRY_NOT_ALLOWED }

class AiAnalysisException(val failure: AiAnalysisFailure, message: String) : RuntimeException(message)

class GetLeadAiAnalysisUseCase(
    private val leads: LeadRepository,
    private val jobs: AiJobRepository,
    private val results: AiAnalysisResultRepository,
) {
    fun get(leadId: Long, reader: LeadReader): LeadAiAnalysisView {
        requireManager(reader.role)
        if (leads.findById(leadId) == null) fail(AiAnalysisFailure.LEAD_NOT_FOUND, "Lead not found")
        val job = jobs.findLatestByLeadId(leadId)
            ?: fail(AiAnalysisFailure.ANALYSIS_NOT_FOUND, "AI analysis was not queued")
        return LeadAiAnalysisView(job, results.findByJobId(checkNotNull(job.id)))
    }
}

class RetryLeadAiAnalysisUseCase(
    private val leads: LeadRepository,
    private val jobs: AiJobRepository,
) {
    fun retry(leadId: Long, actorRole: UserRole, now: Instant): AiJob {
        requireManager(actorRole)
        if (leads.findById(leadId) == null) fail(AiAnalysisFailure.LEAD_NOT_FOUND, "Lead not found")
        val latest = jobs.findLatestByLeadId(leadId)
            ?: fail(AiAnalysisFailure.ANALYSIS_NOT_FOUND, "AI analysis was not queued")
        if (latest.status != AiJobStatus.FAILED) {
            fail(AiAnalysisFailure.RETRY_NOT_ALLOWED, "Only a failed AI analysis can be retried")
        }
        return jobs.enqueue(leadId, now, latest.maxAttempts)
    }
}

private fun requireManager(role: UserRole) {
    if (role == UserRole.CUSTOMER) fail(AiAnalysisFailure.FORBIDDEN, "AI analysis is available only to managers")
}

private fun fail(failure: AiAnalysisFailure, message: String): Nothing =
    throw AiAnalysisException(failure, message)
