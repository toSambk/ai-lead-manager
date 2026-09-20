package dev.aileadmanager.app.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/telegram/webhook")
class TelegramWebhookController {
    @PostMapping
    fun receiveUpdate(): ResponseEntity<Void> = plannedEndpoint()
}
