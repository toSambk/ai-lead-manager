package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.AiAnalysisResponse
import dev.aileadmanager.app.security.CurrentUser
import dev.aileadmanager.app.service.AiAnalysisService
import dev.aileadmanager.core.usecase.AiAnalysisException
import dev.aileadmanager.core.usecase.AiAnalysisFailure
import dev.aileadmanager.core.usecase.GetLeadAiAnalysisUseCase
import dev.aileadmanager.core.usecase.LeadReader
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/leads/{id}")
class LeadAssistanceController(
    private val getAnalysis: GetLeadAiAnalysisUseCase,
    private val aiAnalysisService: AiAnalysisService,
) {
    @GetMapping("/ai-analysis")
    fun getAiAnalysis(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: CurrentUser,
    ): AiAnalysisResponse = try {
        AiAnalysisResponse.from(getAnalysis.get(id, LeadReader(user.id, user.role)))
    } catch (error: AiAnalysisException) {
        throw error.toResponseStatusException()
    }

    @PostMapping("/ai-analysis/retry")
    fun retryAiAnalysis(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: CurrentUser,
    ): ResponseEntity<AiAnalysisResponse> = try {
        ResponseEntity.accepted().body(AiAnalysisResponse.from(aiAnalysisService.retry(id, user.role)))
    } catch (error: AiAnalysisException) {
        throw error.toResponseStatusException()
    }

    @PostMapping("/reply-drafts")
    fun createReplyDraft(): ResponseEntity<Void> = plannedEndpoint()

    @PostMapping("/reply-drafts/{draftId}/send")
    fun approveAndSendReply(): ResponseEntity<Void> = plannedEndpoint()

    private fun AiAnalysisException.toResponseStatusException(): org.springframework.web.server.ResponseStatusException {
        val status = when (failure) {
            AiAnalysisFailure.LEAD_NOT_FOUND, AiAnalysisFailure.ANALYSIS_NOT_FOUND -> HttpStatus.NOT_FOUND
            AiAnalysisFailure.FORBIDDEN -> HttpStatus.FORBIDDEN
            AiAnalysisFailure.RETRY_NOT_ALLOWED -> HttpStatus.CONFLICT
        }
        return org.springframework.web.server.ResponseStatusException(status, message, this)
    }
}
