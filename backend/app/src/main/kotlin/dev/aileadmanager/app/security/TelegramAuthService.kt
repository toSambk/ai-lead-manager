package dev.aileadmanager.app.security

import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.telegram.TelegramInitDataVerifier
import org.springframework.stereotype.Service

@Service
class TelegramAuthService(
    private val verifier: TelegramInitDataVerifier,
    private val users: UserRepository,
) {
    fun authenticate(initData: String): User {
        val verified = verifier.verify(initData)
        return users.upsertTelegramProfile(verified.telegramUserId, verified.displayName)
    }
}
