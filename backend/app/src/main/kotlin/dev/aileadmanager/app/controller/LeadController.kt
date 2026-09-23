package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.CreateLeadRequest
import dev.aileadmanager.app.dto.CreateLeadResponse
import dev.aileadmanager.app.dto.ChangeLeadStatusRequest
import dev.aileadmanager.app.dto.LeadEventResponse
import dev.aileadmanager.app.dto.LeadPageResponse
import dev.aileadmanager.app.dto.LeadResponse
import dev.aileadmanager.app.security.CurrentUser
import dev.aileadmanager.app.service.LeadStatusService
import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.usecase.CreateLeadCommand
import dev.aileadmanager.core.usecase.CreateLeadException
import dev.aileadmanager.core.usecase.CreateLeadFailure
import dev.aileadmanager.core.usecase.CreateLeadUseCase
import dev.aileadmanager.core.usecase.ChangeLeadStatusCommand
import dev.aileadmanager.core.usecase.ChangeLeadStatusException
import dev.aileadmanager.core.usecase.ChangeLeadStatusFailure
import dev.aileadmanager.core.usecase.GetLeadUseCase
import dev.aileadmanager.core.usecase.LeadReader
import dev.aileadmanager.core.usecase.ListLeadEventsUseCase
import dev.aileadmanager.core.usecase.ListLeadsUseCase
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/leads")
class LeadController(
    private val createLeadUseCase: CreateLeadUseCase,
    private val getLeadUseCase: GetLeadUseCase,
    private val listLeadsUseCase: ListLeadsUseCase,
    private val leadStatusService: LeadStatusService,
    private val listLeadEventsUseCase: ListLeadEventsUseCase,
    private val categories: ServiceCategoryRepository,
) {
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
    fun listLeads(
        @AuthenticationPrincipal user: CurrentUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): LeadPageResponse {
        val result = try {
            listLeadsUseCase.list(user.toLeadReader(), page, size)
        } catch (error: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, error.message, error)
        }
        return LeadPageResponse(
            items = result.items.map(::toResponse),
            page = page,
            size = size,
            totalElements = result.totalElements,
            totalPages = if (result.totalElements == 0L) 0 else ((result.totalElements - 1) / size + 1).toInt(),
        )
    }

    @GetMapping("/{id}")
    fun getLead(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: CurrentUser,
    ): LeadResponse {
        val lead = getLeadUseCase.get(id, user.toLeadReader())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found")
        return toResponse(lead)
    }

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @Valid @RequestBody request: ChangeLeadStatusRequest,
        @AuthenticationPrincipal user: CurrentUser,
    ): LeadResponse {
        val result = try {
            leadStatusService.change(ChangeLeadStatusCommand(
                leadId = id,
                actorId = user.id,
                actorRole = user.role,
                status = request.status,
                expectedVersion = request.version,
            ))
        } catch (error: ChangeLeadStatusException) {
            throw error.toResponseStatusException()
        }
        return toResponse(result.lead)
    }

    @PatchMapping("/{id}/assignee")
    fun assignOwner(): ResponseEntity<Void> = plannedEndpoint()

    @PostMapping("/{id}/notes")
    fun addNote(): ResponseEntity<Void> = plannedEndpoint()

    @GetMapping("/{id}/events")
    fun listEvents(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: CurrentUser,
    ): List<LeadEventResponse> = try {
        listLeadEventsUseCase.list(id, user.toLeadReader()).map(LeadEventResponse::from)
    } catch (error: ChangeLeadStatusException) {
        throw error.toResponseStatusException()
    }

    @GetMapping("/{id}/messages")
    fun listMessages(): ResponseEntity<Void> = plannedEndpoint()

    private fun CurrentUser.toLeadReader() = LeadReader(id, role)

    private fun ChangeLeadStatusException.toResponseStatusException(): ResponseStatusException {
        val status = when (failure) {
            ChangeLeadStatusFailure.LEAD_NOT_FOUND -> HttpStatus.NOT_FOUND
            ChangeLeadStatusFailure.FORBIDDEN -> HttpStatus.FORBIDDEN
            ChangeLeadStatusFailure.INVALID_TRANSITION, ChangeLeadStatusFailure.VERSION_CONFLICT -> HttpStatus.CONFLICT
        }
        return ResponseStatusException(status, message, this)
    }

    private fun toResponse(lead: Lead): LeadResponse {
        val category = categories.findById(lead.categoryId)
            ?: error("Category ${lead.categoryId} referenced by lead ${lead.id} was not found")
        return LeadResponse.from(lead, category)
    }
}
