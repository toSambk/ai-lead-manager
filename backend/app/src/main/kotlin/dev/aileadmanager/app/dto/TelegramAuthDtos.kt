package dev.aileadmanager.app.dto

import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class TelegramAuthRequest(
    @field:NotBlank @field:Size(max = 16384) val initData: String,
)

data class CurrentUserResponse(
    val id: Long,
    val displayName: String,
    val role: UserRole,
) {
    companion object {
        fun from(user: User) = CurrentUserResponse(
            id = requireNotNull(user.id),
            displayName = user.displayName,
            role = user.role,
        )
    }
}

data class CsrfResponse(val token: String, val headerName: String)
