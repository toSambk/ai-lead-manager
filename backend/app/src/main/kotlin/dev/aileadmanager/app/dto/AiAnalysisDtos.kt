package dev.aileadmanager.app.dto

import dev.aileadmanager.core.AiAnalysisResult
import dev.aileadmanager.core.AiExtractedFacts
import dev.aileadmanager.core.AiJob
import dev.aileadmanager.core.AiJobErrorCode
import dev.aileadmanager.core.AiJobStatus
import dev.aileadmanager.core.AiMissingField
import dev.aileadmanager.core.AiPriority
import dev.aileadmanager.core.usecase.LeadAiAnalysisView
import java.time.Instant

data class AiAnalysisResponse(
    val jobId: Long,
    val status: AiJobStatus,
    val attemptCount: Int,
    val maxAttempts: Int,
    val nextAttemptAt: Instant,
    val errorCode: AiJobErrorCode?,
    val result: AiAnalysisResultResponse?,
) {
    companion object {
        fun from(view: LeadAiAnalysisView) = from(view.job, view.result)

        fun from(job: AiJob, result: AiAnalysisResult? = null) = AiAnalysisResponse(
            jobId = checkNotNull(job.id) { "Stored AI job has no ID" },
            status = job.status,
            attemptCount = job.attemptCount,
            maxAttempts = job.maxAttempts,
            nextAttemptAt = job.nextAttemptAt,
            errorCode = job.lastErrorCode,
            result = result?.let(AiAnalysisResultResponse::from),
        )
    }
}

data class AiAnalysisResultResponse(
    val summary: String,
    val extractedFacts: AiExtractedFacts,
    val missingFields: Set<AiMissingField>,
    val suggestedQuestion: String?,
    val priority: AiPriority,
    val priorityReason: String,
    val createdAt: Instant,
) {
    companion object {
        fun from(result: AiAnalysisResult) = AiAnalysisResultResponse(
            summary = result.summary,
            extractedFacts = result.extractedFacts,
            missingFields = result.missingFields,
            suggestedQuestion = result.suggestedQuestion,
            priority = result.priority,
            priorityReason = result.priorityReason,
            createdAt = checkNotNull(result.createdAt) { "Stored AI result has no creation time" },
        )
    }
}
