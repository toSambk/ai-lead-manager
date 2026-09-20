package dev.aileadmanager.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class IdentityController {
    @PostMapping("/api/auth/telegram")
    fun authenticateWithTelegram(): ResponseEntity<Void> = plannedEndpoint()

    @GetMapping("/api/me")
    fun currentUser(): ResponseEntity<Void> = plannedEndpoint()

    @GetMapping("/api/categories")
    fun listCategories(): ResponseEntity<Void> = plannedEndpoint()
}
