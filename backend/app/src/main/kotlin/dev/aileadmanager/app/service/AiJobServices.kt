package dev.aileadmanager.app.service

import dev.aileadmanager.core.AiAnalysisInput
import dev.aileadmanager.core.AiAnalysisResult
import dev.aileadmanager.core.AiAnalysisResultRepository
import dev.aileadmanager.core.AiJob
import dev.aileadmanager.core.AiJobErrorCode
import dev.aileadmanager.core.AiJobRepository
import dev.aileadmanager.core.AiProvider
import dev.aileadmanager.core.AiProviderException
import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.usecase.AiAnalysisValidator
import dev.aileadmanager.core.usecase.InvalidAiAnalysis
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.pow
import kotlin.random.Random
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class AiJobClaimService(
    private val jobs: AiJobRepository,
    private val clock: Clock,
) {
    @Transactional
    fun claim(workerId: String, limit: Int, lockTimeout: Duration): List<AiJob> {
        val now = Instant.now(clock)
        return jobs.claimAvailable(workerId, now, now.plus(lockTimeout), limit)
    }

    @Transactional
    fun recoverStale(): Int = jobs.recoverStale(Instant.now(clock))
}

@Service
class AiAnalysisCompletionService(
    private val results: AiAnalysisResultRepository,
    private val jobs: AiJobRepository,
    private val leads: LeadRepository,
    private val events: LeadEventRepository,
    private val clock: Clock,
) {
    @Transactional
    fun complete(job: AiJob, result: AiAnalysisResult) {
        results.save(result)
        val lead = leads.findById(job.leadId)
            ?: error("Lead ${job.leadId} disappeared while its AI job was running")
        if (lead.status == LeadStatus.NEW && result.missingFields.isNotEmpty()) {
            val updated = leads.save(lead.copy(status = LeadStatus.CLARIFICATION))
            events.save(LeadEvent(
                leadId = job.leadId,
                actorId = null,
                type = LeadEventType.STATUS_CHANGED,
                oldStatus = lead.status,
                newStatus = updated.status,
            ))
        }
        jobs.markSucceeded(checkNotNull(job.id), Instant.now(clock))
    }
}

@Service
class AiJobFailureService(
    private val jobs: AiJobRepository,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(job: AiJob, errorCode: AiJobErrorCode, retryable: Boolean, retryAfter: Duration?) {
        val now = Instant.now(clock)
        val jobId = checkNotNull(job.id)
        val safeMessage = safeMessages.getValue(errorCode)
        if (!retryable || job.attemptCount >= job.maxAttempts) {
            jobs.markFailed(jobId, errorCode, safeMessage, now)
            return
        }
        val delay = retryAfter ?: retryDelay(job.attemptCount)
        jobs.reschedule(jobId, now.plus(delay.coerceAtMost(MAX_RETRY_DELAY)), errorCode, safeMessage, now)
    }

    private fun retryDelay(attempt: Int): Duration {
        val baseSeconds = 10.0 * 2.0.pow((attempt - 1).coerceAtLeast(0))
        val jitter = Random.nextDouble(0.8, 1.2)
        return Duration.ofMillis((baseSeconds * jitter * 1000).toLong())
    }

    private companion object {
        val MAX_RETRY_DELAY: Duration = Duration.ofMinutes(30)
        val safeMessages = mapOf(
            AiJobErrorCode.PROVIDER_TIMEOUT to "AI provider request timed out",
            AiJobErrorCode.PROVIDER_RATE_LIMITED to "AI provider rate limit was reached",
            AiJobErrorCode.PROVIDER_UNAVAILABLE to "AI provider is temporarily unavailable",
            AiJobErrorCode.PROVIDER_AUTHENTICATION_FAILED to "AI provider authentication failed",
            AiJobErrorCode.INVALID_PROVIDER_RESPONSE to "AI provider returned an invalid response",
            AiJobErrorCode.CONTENT_REJECTED to "AI provider rejected the content",
            AiJobErrorCode.LEAD_NOT_FOUND to "Lead was not found",
            AiJobErrorCode.INTERNAL_ERROR to "Internal AI processing error",
        )
    }
}

@Service
class AiJobProcessor(
    private val leads: LeadRepository,
    private val categories: ServiceCategoryRepository,
    private val provider: AiProvider,
    private val validator: AiAnalysisValidator,
    private val completion: AiAnalysisCompletionService,
    private val failures: AiJobFailureService,
) {
    fun process(job: AiJob) {
        try {
            val lead = leads.findById(job.leadId)
            if (lead == null) {
                failures.record(job, AiJobErrorCode.LEAD_NOT_FOUND, retryable = false, retryAfter = null)
                return
            }
            val category = categories.findById(lead.categoryId)
                ?: error("Category ${lead.categoryId} referenced by lead ${lead.id} was not found")
            val draft = provider.analyze(AiAnalysisInput(lead, category))
            completion.complete(job, validator.validate(job, draft))
        } catch (error: AiProviderException) {
            log.warn("AI job {} failed with provider error {}", job.id, error.errorCode, error)
            failures.record(job, error.errorCode, error.retryable, error.retryAfter)
        } catch (error: InvalidAiAnalysis) {
            log.warn("AI job {} returned invalid structured output", job.id, error)
            failures.record(job, AiJobErrorCode.INVALID_PROVIDER_RESPONSE, retryable = true, retryAfter = null)
        } catch (error: Exception) {
            log.error("AI job {} failed with an internal error", job.id, error)
            failures.record(job, AiJobErrorCode.INTERNAL_ERROR, retryable = true, retryAfter = null)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AiJobProcessor::class.java)
    }
}
