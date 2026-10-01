package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.AiAnalysisResponse
import dev.aileadmanager.app.dto.CreateReplyDraftRequest
import dev.aileadmanager.app.dto.ReplyDraftResponse
import dev.aileadmanager.app.dto.SendReplyDraftRequest
import dev.aileadmanager.app.dto.UpdateReplyDraftRequest
import dev.aileadmanager.app.security.CurrentUser
import dev.aileadmanager.app.service.ai.AiAnalysisService
import dev.aileadmanager.app.service.lead.LeadClarificationService
import dev.aileadmanager.app.service.lead.ReplyDraftService
import dev.aileadmanager.core.usecase.AiAnalysisException
import dev.aileadmanager.core.usecase.AiAnalysisFailure
import dev.aileadmanager.core.usecase.GetLeadAiAnalysisUseCase
import dev.aileadmanager.core.usecase.LeadReader
import dev.aileadmanager.core.usecase.LeadClarificationException
import dev.aileadmanager.core.usecase.LeadClarificationFailure
import dev.aileadmanager.core.usecase.ListReplyDraftsUseCase
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/leads/{id}")
class LeadAssistanceController(
    private val getAnalysis: GetLeadAiAnalysisUseCase,
    private val aiAnalysisService: AiAnalysisService,
    private val replyDraftService: ReplyDraftService,
    private val listReplyDrafts: ListReplyDraftsUseCase,
    private val clarificationService: LeadClarificationService,
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
    fun createReplyDraft(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateReplyDraftRequest,
        @AuthenticationPrincipal user: CurrentUser,
    ): ResponseEntity<ReplyDraftResponse> = try {
        val draft = replyDraftService.create(id, user.id, user.role, request.body)
        ResponseEntity.created(URI.create("/api/leads/$id/reply-drafts/${draft.id}"))
            .body(ReplyDraftResponse.from(draft))
    } catch (error: LeadClarificationException) {
        throw error.toResponseStatusException()
    }

    @GetMapping("/reply-drafts")
    fun listReplyDrafts(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: CurrentUser,
    ): List<ReplyDraftResponse> = try {
        listReplyDrafts.list(id, LeadReader(user.id, user.role)).map(ReplyDraftResponse::from)
    } catch (error: LeadClarificationException) {
        throw error.toResponseStatusException()
    }

    @PatchMapping("/reply-drafts/{draftId}")
    fun updateReplyDraft(
        @PathVariable id: Long,
        @PathVariable draftId: Long,
        @Valid @RequestBody request: UpdateReplyDraftRequest,
        @AuthenticationPrincipal user: CurrentUser,
    ): ReplyDraftResponse = try {
        ReplyDraftResponse.from(replyDraftService.update(
            id,
            draftId,
            user.role,
            request.body,
            request.version,
        ))
    } catch (error: LeadClarificationException) {
        throw error.toResponseStatusException()
    }

    @PostMapping("/reply-drafts/{draftId}/send")
    fun approveAndSendReply(
        @PathVariable id: Long,
        @PathVariable draftId: Long,
        @Valid @RequestBody request: SendReplyDraftRequest,
        @AuthenticationPrincipal user: CurrentUser,
    ): ResponseEntity<ReplyDraftResponse> = try {
        ResponseEntity.accepted().body(ReplyDraftResponse.from(
            clarificationService.approveAndSend(id, draftId, user.role, request.version),
        ))
    } catch (error: LeadClarificationException) {
        throw error.toResponseStatusException()
    }

    private fun AiAnalysisException.toResponseStatusException(): org.springframework.web.server.ResponseStatusException {
        val status = when (failure) {
            AiAnalysisFailure.LEAD_NOT_FOUND, AiAnalysisFailure.ANALYSIS_NOT_FOUND -> HttpStatus.NOT_FOUND
            AiAnalysisFailure.FORBIDDEN -> HttpStatus.FORBIDDEN
            AiAnalysisFailure.RETRY_NOT_ALLOWED -> HttpStatus.CONFLICT
        }
        return org.springframework.web.server.ResponseStatusException(status, message, this)
    }

    private fun LeadClarificationException.toResponseStatusException(): org.springframework.web.server.ResponseStatusException {
        val status = when (failure) {
            LeadClarificationFailure.LEAD_NOT_FOUND, LeadClarificationFailure.DRAFT_NOT_FOUND -> HttpStatus.NOT_FOUND
            LeadClarificationFailure.FORBIDDEN -> HttpStatus.FORBIDDEN
            LeadClarificationFailure.INVALID_BODY -> HttpStatus.BAD_REQUEST
            LeadClarificationFailure.CHAT_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_ENTITY
            LeadClarificationFailure.INVALID_LEAD_STATUS,
            LeadClarificationFailure.INVALID_DRAFT_STATUS,
            LeadClarificationFailure.VERSION_CONFLICT,
            LeadClarificationFailure.CONVERSATION_ACTIVE,
            LeadClarificationFailure.AI_ANALYSIS_ACTIVE -> HttpStatus.CONFLICT
        }
        return org.springframework.web.server.ResponseStatusException(status, message, this)
    }
}
