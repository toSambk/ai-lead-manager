package dev.aileadmanager.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/leads")
class LeadController {
    @PostMapping
    fun createLead(): ResponseEntity<Void> = plannedEndpoint()

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
