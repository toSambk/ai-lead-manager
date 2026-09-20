package dev.aileadmanager.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/leads/{id}")
class LeadAssistanceController {
    @GetMapping("/ai-analysis")
    fun getAiAnalysis(): ResponseEntity<Void> = plannedEndpoint()

    @PostMapping("/ai-analysis/retry")
    fun retryAiAnalysis(): ResponseEntity<Void> = plannedEndpoint()

    @PostMapping("/reply-drafts")
    fun createReplyDraft(): ResponseEntity<Void> = plannedEndpoint()

    @PostMapping("/reply-drafts/{draftId}/send")
    fun approveAndSendReply(): ResponseEntity<Void> = plannedEndpoint()
}
