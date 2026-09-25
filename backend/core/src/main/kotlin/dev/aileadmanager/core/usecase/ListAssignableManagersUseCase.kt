package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository

class ListAssignableManagersUseCase(private val users: UserRepository) {
    fun list(): List<User> = users.findAssignableManagers()
}
