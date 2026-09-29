package dev.aileadmanager.core

import java.time.Instant

enum class TelegramDeliveryStatus { PENDING, RUNNING, SUCCEEDED, FAILED }

enum class TelegramDeliveryType {
    START_REPLY,
    HELP_REPLY,
    MANAGER_NEW_LEAD,
    CUSTOMER_LEAD_CONFIRMATION,
    MANAGER_APPROVED_REPLY,
}

enum class TelegramDeliveryErrorCode {
    NETWORK_ERROR,
    RATE_LIMITED,
    PROVIDER_UNAVAILABLE,
    AUTHENTICATION_FAILED,
    CHAT_UNAVAILABLE,
    INVALID_REQUEST,
    INTERNAL_ERROR,
}

data class TelegramDeliveryJob(
    val id: Long? = null,
    val messageKey: String,
    val type: TelegramDeliveryType,
    val chatId: Long,
    val leadId: Long? = null,
    val replyDraftId: Long? = null,
    val leadMessageId: Long? = null,
    val conversationId: Long? = null,
    val text: String,
    val buttonText: String? = null,
    val buttonUrl: String? = null,
    val status: TelegramDeliveryStatus = TelegramDeliveryStatus.PENDING,
    val attemptCount: Int = 0,
    val maxAttempts: Int = 5,
    val nextAttemptAt: Instant,
    val lockedAt: Instant? = null,
    val lockedUntil: Instant? = null,
    val lockedBy: String? = null,
    val lastErrorCode: TelegramDeliveryErrorCode? = null,
    val lastErrorMessage: String? = null,
    val telegramMessageId: Long? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val deliveredAt: Instant? = null,
)

interface TelegramDeliveryJobRepository {
    fun enqueue(job: TelegramDeliveryJob): TelegramDeliveryJob
    fun findByMessageKey(messageKey: String): TelegramDeliveryJob?
    fun claimAvailable(workerId: String, now: Instant, lockedUntil: Instant, limit: Int): List<TelegramDeliveryJob>
    fun markSucceeded(jobId: Long, telegramMessageId: Long, now: Instant)
    fun reschedule(
        jobId: Long,
        nextAttemptAt: Instant,
        errorCode: TelegramDeliveryErrorCode,
        errorMessage: String,
        now: Instant,
    )
    fun markFailed(jobId: Long, errorCode: TelegramDeliveryErrorCode, errorMessage: String, now: Instant)
    fun recoverStale(now: Instant): List<TelegramDeliveryJob>
}

data class TelegramChatBinding(
    val userId: Long,
    val telegramChatId: Long,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
)

interface TelegramChatBindingRepository {
    fun savePrivateChat(userId: Long, telegramChatId: Long, now: Instant): TelegramChatBinding
    fun findPrivateChatByUserId(userId: Long): TelegramChatBinding?
}

interface TelegramUpdateRepository {
    fun recordIfNew(updateId: Long, updateType: String, payload: String, receivedAt: Instant): Boolean
    fun markProcessed(updateId: Long, processedAt: Instant)
}
