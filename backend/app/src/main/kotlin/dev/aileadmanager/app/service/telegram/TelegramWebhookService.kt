package dev.aileadmanager.app.service.telegram

import dev.aileadmanager.app.service.lead.LeadClarificationService
import dev.aileadmanager.core.TelegramChatBindingRepository
import dev.aileadmanager.core.TelegramUpdateRepository
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.telegram.TelegramIncomingMessage
import dev.aileadmanager.telegram.TelegramUpdateParser
import dev.aileadmanager.telegram.botCommand
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelegramWebhookService(
    private val parser: TelegramUpdateParser,
    private val updates: TelegramUpdateRepository,
    private val users: UserRepository,
    private val chats: TelegramChatBindingRepository,
    private val notifications: TelegramNotificationService,
    private val clarifications: LeadClarificationService,
    private val clock: Clock,
) {
    @Transactional
    fun accept(rawUpdate: String) {
        val update = parser.parse(rawUpdate)
        val now = Instant.now(clock)
        if (!updates.recordIfNew(update.id, update.type, rawUpdate, now)) return
        update.message?.let { handleMessage(update.id, it, now) }
        updates.markProcessed(update.id, Instant.now(clock))
    }

    private fun handleMessage(updateId: Long, message: TelegramIncomingMessage, now: Instant) {
        val sender = message.from ?: return
        val user = if (message.chat.type == "private") {
            val user = users.upsertTelegramProfile(sender.id, sender.displayName)
            chats.savePrivateChat(checkNotNull(user.id), message.chat.id, now)
            user
        } else null
        val includeMiniAppButton = message.chat.type == "private"
        when (message.botCommand()) {
            "/start" -> notifications.enqueueStartReply(updateId, message.chat.id, includeMiniAppButton)
            "/help" -> notifications.enqueueHelpReply(updateId, message.chat.id, includeMiniAppButton)
            null -> if (user != null && !message.text.isNullOrBlank()) {
                val replyText = checkNotNull(message.text)
                clarifications.recordCustomerReply(
                    customerId = checkNotNull(user.id),
                    telegramChatId = message.chat.id,
                    telegramMessageId = message.id,
                    text = replyText,
                )
            } else Unit
            else -> notifications.enqueueHelpReply(updateId, message.chat.id, includeMiniAppButton)
        }
    }
}
