package dev.aileadmanager.app.dto

import dev.aileadmanager.core.UserRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class DevUserResponse(
    val key: String,
    val displayName: String,
    val role: UserRole,
)

data class DevLoginRequest(
    @field:NotBlank @field:Size(max = 64) val userKey: String,
)
