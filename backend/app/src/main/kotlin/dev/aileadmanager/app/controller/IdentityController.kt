package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.CsrfResponse
import dev.aileadmanager.app.dto.CurrentUserResponse
import dev.aileadmanager.app.dto.TelegramAuthRequest
import dev.aileadmanager.app.security.CurrentUser
import dev.aileadmanager.app.security.SessionUserFilter
import dev.aileadmanager.app.security.TelegramAuthService
import dev.aileadmanager.telegram.InvalidTelegramInitData
import dev.aileadmanager.telegram.TelegramAuthUnavailable
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class IdentityController(
    private val authService: TelegramAuthService,
    private val csrfTokens: CsrfTokenRepository,
) {
    private val logger = LoggerFactory.getLogger(IdentityController::class.java)

    @GetMapping("/api/auth/csrf")
    fun csrf(csrfToken: CsrfToken): CsrfResponse = CsrfResponse(csrfToken.token, csrfToken.headerName)

    @PostMapping("/api/auth/telegram")
    fun authenticateWithTelegram(
        @Valid @RequestBody body: TelegramAuthRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): CurrentUserResponse {
        val user = try {
            authService.authenticate(body.initData)
        } catch (exc: InvalidTelegramInitData) {
            logger.warn(
                "Telegram authentication failed: ${exc.message}; initDataLength=${body.initData.length}", exc,
            )
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Telegram authentication data", exc)
        } catch (exc: TelegramAuthUnavailable) {
            logger.error("Telegram authentication is unavailable", exc)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram authentication is not configured",
                exc,
            )
        }
        val session = request.getSession(true)
        request.changeSessionId()
        session.setAttribute(SessionUserFilter.USER_ID_ATTRIBUTE, requireNotNull(user.id))
        csrfTokens.saveToken(null, request, response)
        return CurrentUserResponse.from(user)
    }

    @GetMapping("/api/me")
    fun currentUser(@AuthenticationPrincipal user: CurrentUser): CurrentUserResponse =
        CurrentUserResponse(user.id, user.displayName, user.role)

    @PostMapping("/api/auth/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }

}
