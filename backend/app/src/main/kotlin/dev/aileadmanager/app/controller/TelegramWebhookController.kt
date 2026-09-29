package dev.aileadmanager.app.controller

import dev.aileadmanager.app.service.TelegramWebhookService
import dev.aileadmanager.telegram.InvalidTelegramUpdate
import dev.aileadmanager.telegram.InvalidTelegramWebhookSecret
import dev.aileadmanager.telegram.TelegramWebhookSecretVerifier
import dev.aileadmanager.telegram.TelegramWebhookUnavailable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/telegram/webhook")
class TelegramWebhookController(
    private val verifier: TelegramWebhookSecretVerifier,
    private val service: TelegramWebhookService,
) {
    @PostMapping
    fun receiveUpdate(
        @RequestHeader("X-Telegram-Bot-Api-Secret-Token", required = false) secret: String?,
        @RequestBody rawUpdate: String,
    ): ResponseEntity<Void> {
        try {
            verifier.verify(secret)
            service.accept(rawUpdate)
        } catch (error: InvalidTelegramWebhookSecret) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Telegram webhook secret", error)
        } catch (error: TelegramWebhookUnavailable) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram webhook is not configured", error)
        } catch (error: InvalidTelegramUpdate) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Telegram update", error)
        }
        return ResponseEntity.ok().build()
    }
}
