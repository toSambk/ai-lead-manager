package dev.aileadmanager.core

import java.time.Duration
import java.time.Instant

enum class AiJobStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

enum class AiJobErrorCode {
    PROVIDER_TIMEOUT,
    PROVIDER_RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    PROVIDER_AUTHENTICATION_FAILED,
    INVALID_PROVIDER_RESPONSE,
    CONTENT_REJECTED,
    LEAD_NOT_FOUND,
    INTERNAL_ERROR,
}

enum class AiPriority { LOW, MEDIUM, HIGH }

enum class AiMissingField { BUDGET, DEADLINE }

data class AiJob(
    val id: Long? = null,
    val leadId: Long,
    val status: AiJobStatus = AiJobStatus.PENDING,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 5,
    val nextAttemptAt: Instant,
    val lockedAt: Instant? = null,
    val lockedUntil: Instant? = null,
    val lockedBy: String? = null,
    val lastErrorCode: AiJobErrorCode? = null,
    val lastErrorMessage: String? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

data class AiExtractedFacts(
    val category: String,
    val budgetAmount: String? = null,
    val budgetCurrency: String? = null,
    val desiredDeadline: String? = null,
)

data class AiAnalysisDraft(
    val summary: String,
    val extractedFacts: AiExtractedFacts,
    val missingFields: Set<AiMissingField>,
    val suggestedQuestion: String?,
    val priority: AiPriority,
    val priorityReason: String,
)

data class AiAnalysisResult(
    val id: Long? = null,
    val jobId: Long,
    val leadId: Long,
    val summary: String,
    val extractedFacts: AiExtractedFacts,
    val missingFields: Set<AiMissingField>,
    val suggestedQuestion: String?,
    val priority: AiPriority,
    val priorityReason: String,
    val createdAt: Instant? = null,
)

data class AiAnalysisInput(
    val lead: Lead,
    val category: ServiceCategory,
)

interface AiProvider {
    fun analyze(input: AiAnalysisInput): AiAnalysisDraft
}

class AiProviderException(
    val errorCode: AiJobErrorCode,
    val retryable: Boolean,
    val retryAfter: Duration? = null,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

interface AiJobRepository {
    fun enqueue(leadId: Long, now: Instant, maxAttempts: Int = 5): AiJob
    fun findById(id: Long): AiJob?
    fun findLatestByLeadId(leadId: Long): AiJob?
    fun claimAvailable(workerId: String, now: Instant, lockedUntil: Instant, limit: Int): List<AiJob>
    fun markSucceeded(jobId: Long, now: Instant)
    fun reschedule(jobId: Long, nextAttemptAt: Instant, errorCode: AiJobErrorCode, errorMessage: String, now: Instant)
    fun markFailed(jobId: Long, errorCode: AiJobErrorCode, errorMessage: String, now: Instant)
    fun recoverStale(now: Instant): Int
}

interface AiAnalysisResultRepository {
    fun save(result: AiAnalysisResult): AiAnalysisResult
    fun findByJobId(jobId: Long): AiAnalysisResult?
    fun findLatestByLeadId(leadId: Long): AiAnalysisResult?
}
