package dev.aileadmanager.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdminController {
    @GetMapping("/managers")
    fun listManagers(): ResponseEntity<Void> = plannedEndpoint()

    @PatchMapping("/users/{id}/role")
    fun changeUserRole(): ResponseEntity<Void> = plannedEndpoint()

    @PostMapping("/categories")
    fun createCategory(): ResponseEntity<Void> = plannedEndpoint()

    @PatchMapping("/categories/{id}")
    fun updateCategory(): ResponseEntity<Void> = plannedEndpoint()

    @GetMapping("/ai-settings")
    fun getAiSettings(): ResponseEntity<Void> = plannedEndpoint()

    @PatchMapping("/ai-settings")
    fun updateAiSettings(): ResponseEntity<Void> = plannedEndpoint()
}
