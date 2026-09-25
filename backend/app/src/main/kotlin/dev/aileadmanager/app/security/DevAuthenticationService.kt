package dev.aileadmanager.app.security

import dev.aileadmanager.app.dto.DevUserResponse
import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.UserRole
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("local")
class DevAuthenticationService(private val users: UserRepository) {
    fun availableUsers(): List<DevUserResponse> = definitions.map {
        DevUserResponse(it.key, it.displayName, it.role)
    }

    @Transactional
    fun seedUsers() {
        definitions.forEach(::upsert)
    }

    @Transactional
    fun authenticate(userKey: String): User {
        val definition = definitions.firstOrNull { it.key == userKey }
            ?: throw UnknownDevUser(userKey)
        return upsert(definition)
    }

    private fun upsert(definition: DevUserDefinition): User {
        val profile = users.upsertTelegramProfile(definition.telegramUserId, definition.displayName)
        return if (profile.role == definition.role) profile else users.save(profile.copy(role = definition.role))
    }

    private data class DevUserDefinition(
        val key: String,
        val telegramUserId: Long,
        val displayName: String,
        val role: UserRole,
    )

    private companion object {
        val definitions = listOf(
            DevUserDefinition("customer-alice", 9_000_000_000_001, "Alice Customer", UserRole.CUSTOMER),
            DevUserDefinition("customer-bob", 9_000_000_000_002, "Bob Customer", UserRole.CUSTOMER),
            DevUserDefinition("manager-mike", 9_000_000_000_101, "Mike Manager", UserRole.MANAGER),
            DevUserDefinition("manager-kate", 9_000_000_000_102, "Kate Manager", UserRole.MANAGER),
            DevUserDefinition("admin-alex", 9_000_000_000_201, "Alex Administrator", UserRole.ADMIN),
        )
    }
}

class UnknownDevUser(userKey: String) : RuntimeException("Unknown local development user: $userKey")
