package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.CreateLeadRequest
import dev.aileadmanager.app.dto.CreateLeadResponse
import dev.aileadmanager.app.security.CurrentUser
import dev.aileadmanager.core.usecase.CreateLeadCommand
import dev.aileadmanager.core.usecase.CreateLeadException
import dev.aileadmanager.core.usecase.CreateLeadFailure
import dev.aileadmanager.core.usecase.CreateLeadUseCase
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.security.core.annotation.AuthenticationPrincipal

@RestController
@RequestMapping("/api/leads")
class LeadController(private val createLeadUseCase: CreateLeadUseCase) {
    @PostMapping
    fun createLead(
        @Valid @RequestBody request: CreateLeadRequest,
        @AuthenticationPrincipal customer: CurrentUser,
    ): ResponseEntity<CreateLeadResponse> {
        val lead = try {
            createLeadUseCase.create(CreateLeadCommand(
                customerId = customer.id,
                categoryId = request.categoryId,
                description = request.description,
                estimatedBudgetAmount = request.estimatedBudgetAmount,
                budgetCurrency = request.budgetCurrency,
                desiredDeadline = request.desiredDeadline,
                contactDetails = request.contactDetails,
            ))
        } catch (error: CreateLeadException) {
            val status = when (error.failure) {
                CreateLeadFailure.INVALID_INPUT, CreateLeadFailure.CATEGORY_UNAVAILABLE -> HttpStatus.BAD_REQUEST
                CreateLeadFailure.CUSTOMER_UNAVAILABLE -> HttpStatus.UNAUTHORIZED
            }
            throw ResponseStatusException(status, error.message, error)
        }
        val id = checkNotNull(lead.id) { "Saved lead has no ID" }
        val createdAt = checkNotNull(lead.createdAt) { "Saved lead has no creation time" }
        return ResponseEntity.created(URI.create("/api/leads/$id")).body(CreateLeadResponse(
            id = id,
            reference = "LM-${id.toString().padStart(6, '0')}",
            status = lead.status,
            createdAt = createdAt,
        ))
    }

    @GetMapping
    fun listLeads(): ResponseEntity<Void> = plannedEndpoint()

    @GetMapping("/{id}")
    fun getLead(): ResponseEntity<Void> = plannedEndpoint()

    @PatchMapping("/{id}/status")
    fun changeStatus(): ResponseEntity<Void> = plannedEndpoint()

    @PatchMapping("/{id}/assignee")
    fun assignOwner(): ResponseEntity<Void> = plannedEndpoint()

    @PostMapping("/{id}/notes")
    fun addNote(): ResponseEntity<Void> = plannedEndpoint()

    @GetMapping("/{id}/events")
    fun listEvents(): ResponseEntity<Void> = plannedEndpoint()

    @GetMapping("/{id}/messages")
    fun listMessages(): ResponseEntity<Void> = plannedEndpoint()
}
