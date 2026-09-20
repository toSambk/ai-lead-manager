package dev.aileadmanager.app.security

import dev.aileadmanager.core.UserRole

data class CurrentUser(
    val id: Long,
    val telegramUserId: Long,
    val displayName: String,
    val role: UserRole,
)
