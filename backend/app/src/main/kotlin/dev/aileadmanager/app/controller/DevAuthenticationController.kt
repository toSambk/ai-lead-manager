package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.CurrentUserResponse
import dev.aileadmanager.app.dto.DevLoginRequest
import dev.aileadmanager.app.dto.DevUserResponse
import dev.aileadmanager.app.security.DevAuthenticationService
import dev.aileadmanager.app.security.SessionUserFilter
import dev.aileadmanager.app.security.UnknownDevUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/dev/auth")
@Profile("local")
class DevAuthenticationController(
    private val devAuthentication: DevAuthenticationService,
    private val csrfTokens: CsrfTokenRepository,
) {
    @GetMapping("/users")
    fun users(): List<DevUserResponse> = devAuthentication.availableUsers()

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody body: DevLoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): CurrentUserResponse {
        val user = try {
            devAuthentication.authenticate(body.userKey)
        } catch (exception: UnknownDevUser) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Local development user was not found", exception)
        }
        val session = request.getSession(true)
        request.changeSessionId()
        session.setAttribute(SessionUserFilter.USER_ID_ATTRIBUTE, requireNotNull(user.id))
        csrfTokens.saveToken(null, request, response)
        return CurrentUserResponse.from(user)
    }
}
