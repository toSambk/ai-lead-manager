package dev.aileadmanager.core

import java.time.Instant

enum class UserRole { CUSTOMER, MANAGER, ADMIN }

data class User(
    val id: Long? = null,
    val telegramUserId: Long,
    val role: UserRole = UserRole.CUSTOMER,
    val displayName: String,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

interface UserRepository {
    fun save(user: User): User
    fun upsertTelegramProfile(telegramUserId: Long, displayName: String): User
    fun findById(id: Long): User?
    fun findByTelegramUserId(telegramUserId: Long): User?
    fun findAssignableManagers(): List<User>
}
