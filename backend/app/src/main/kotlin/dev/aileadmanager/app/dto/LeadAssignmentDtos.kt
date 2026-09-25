package dev.aileadmanager.app.dto

import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRole
import jakarta.validation.constraints.PositiveOrZero

data class AssignLeadOwnerRequest(
    val ownerId: Long?,
    @field:PositiveOrZero val version: Long,
)

data class AssignableManagerResponse(
    val id: Long,
    val displayName: String,
    val role: UserRole,
) {
    companion object {
        fun from(user: User) = AssignableManagerResponse(
            id = checkNotNull(user.id) { "Stored user has no ID" },
            displayName = user.displayName,
            role = user.role,
        )
    }
}
