package dev.aileadmanager.app.service

import dev.aileadmanager.core.AiJob
import dev.aileadmanager.core.UserRole
import dev.aileadmanager.core.usecase.RetryLeadAiAnalysisUseCase
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

@Service
class AiAnalysisService(
    private val retryUseCase: RetryLeadAiAnalysisUseCase,
    private val clock: Clock,
) {
    @Transactional
    fun retry(leadId: Long, actorRole: UserRole): AiJob = try {
        retryUseCase.retry(leadId, actorRole, Instant.now(clock))
    } catch (error: DataIntegrityViolationException) {
        throw dev.aileadmanager.core.usecase.AiAnalysisException(
            dev.aileadmanager.core.usecase.AiAnalysisFailure.RETRY_NOT_ALLOWED,
            "An AI analysis is already active",
        )
    }
}
