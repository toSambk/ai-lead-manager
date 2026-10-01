package dev.aileadmanager.app.service.telegram

import dev.aileadmanager.app.config.TelegramRuntimeSettings
import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.TelegramChatBindingRepository
import dev.aileadmanager.core.TelegramDeliveryJob
import dev.aileadmanager.core.TelegramDeliveryJobRepository
import dev.aileadmanager.core.TelegramDeliveryType
import dev.aileadmanager.core.UserRepository
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class TelegramNotificationService(
    private val jobs: TelegramDeliveryJobRepository,
    private val chats: TelegramChatBindingRepository,
    private val categories: ServiceCategoryRepository,
    private val users: UserRepository,
    private val settings: TelegramRuntimeSettings,
    private val clock: Clock,
) {
    fun enqueueLeadCreated(lead: Lead) {
        val now = Instant.now(clock)
        val leadId = checkNotNull(lead.id)
        val reference = leadReference(leadId)
        settings.managerChatId?.let { chatId ->
            val category = categories.findById(lead.categoryId)
                ?: error("Category ${lead.categoryId} referenced by lead $leadId was not found")
            val customer = users.findById(lead.customerId)
                ?: error("Customer ${lead.customerId} referenced by lead $leadId was not found")
            jobs.enqueue(TelegramDeliveryJob(
                messageKey = "NEW_LEAD_MANAGER:$leadId",
                type = TelegramDeliveryType.MANAGER_NEW_LEAD,
                chatId = chatId,
                leadId = leadId,
                text = "New lead $reference\n\nCategory: ${category.name}\nStatus: New\nCustomer: ${customer.displayName}",
                nextAttemptAt = now,
            ))
        }
        chats.findPrivateChatByUserId(lead.customerId)?.let { binding ->
            jobs.enqueue(TelegramDeliveryJob(
                messageKey = "LEAD_CREATED_CUSTOMER:$leadId",
                type = TelegramDeliveryType.CUSTOMER_LEAD_CONFIRMATION,
                chatId = binding.telegramChatId,
                leadId = leadId,
                text = "Your request $reference has been received. You can track its status in the Mini App.",
                buttonText = settings.miniAppUrl?.let { "Open Mini App" },
                buttonUrl = settings.miniAppUrl,
                nextAttemptAt = now,
            ))
        }
    }

    fun enqueueStartReply(updateId: Long, chatId: Long, includeMiniAppButton: Boolean) {
        val now = Instant.now(clock)
        val miniAppUrl = settings.miniAppUrl.takeIf { includeMiniAppButton }
        jobs.enqueue(TelegramDeliveryJob(
            messageKey = "START_REPLY:$updateId",
            type = TelegramDeliveryType.START_REPLY,
            chatId = chatId,
            text = "Welcome to AI Lead Manager.\n\nSubmit your request and our team will review it.",
            buttonText = miniAppUrl?.let { "Submit a request" },
            buttonUrl = miniAppUrl,
            nextAttemptAt = now,
        ))
    }

    fun enqueueHelpReply(updateId: Long, chatId: Long, includeMiniAppButton: Boolean) {
        val miniAppUrl = settings.miniAppUrl.takeIf { includeMiniAppButton }
        jobs.enqueue(TelegramDeliveryJob(
            messageKey = "HELP_REPLY:$updateId",
            type = TelegramDeliveryType.HELP_REPLY,
            chatId = chatId,
            text = "Use Submit a request to create a new request. You can track your requests in the Mini App.",
            buttonText = miniAppUrl?.let { "Submit a request" },
            buttonUrl = miniAppUrl,
            nextAttemptAt = Instant.now(clock),
        ))
    }

    private fun leadReference(id: Long) = "LM-${id.toString().padStart(6, '0')}"
}
