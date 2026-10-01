package dev.aileadmanager.app.service.telegram

import dev.aileadmanager.core.TelegramDeliveryErrorCode
import dev.aileadmanager.core.TelegramDeliveryJob
import dev.aileadmanager.core.TelegramDeliveryJobRepository
import dev.aileadmanager.core.LeadMessageRepository
import dev.aileadmanager.core.ReplyDraftRepository
import dev.aileadmanager.core.TelegramConversationRepository
import dev.aileadmanager.telegram.TelegramBotApiException
import dev.aileadmanager.telegram.TelegramBotClient
import dev.aileadmanager.telegram.TelegramOutboundMessage
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.pow
import kotlin.random.Random
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class TelegramDeliveryClaimService(
    private val jobs: TelegramDeliveryJobRepository,
    private val messages: LeadMessageRepository,
    private val drafts: ReplyDraftRepository,
    private val conversations: TelegramConversationRepository,
    private val clock: Clock,
) {
    @Transactional
    fun claim(workerId: String, limit: Int, lockTimeout: Duration): List<TelegramDeliveryJob> {
        val now = Instant.now(clock)
        return jobs.claimAvailable(workerId, now, now.plus(lockTimeout), limit)
    }

    @Transactional
    fun recoverStale(): Int {
        val now = Instant.now(clock)
        val recovered = jobs.recoverStale(now)
        recovered
            .filter { it.status == dev.aileadmanager.core.TelegramDeliveryStatus.FAILED }
            .filter { it.type == dev.aileadmanager.core.TelegramDeliveryType.MANAGER_APPROVED_REPLY }
            .forEach { job ->
                messages.markDeliveryFailed(checkNotNull(job.leadMessageId))
                drafts.markFailed(checkNotNull(job.replyDraftId), now)
                conversations.markCancelled(checkNotNull(job.conversationId), now)
            }
        return recovered.size
    }
}

@Service
class TelegramDeliveryCompletionService(
    private val jobs: TelegramDeliveryJobRepository,
    private val messages: LeadMessageRepository,
    private val drafts: ReplyDraftRepository,
    private val conversations: TelegramConversationRepository,
    private val clock: Clock,
) {
    @Transactional
    fun complete(job: TelegramDeliveryJob, telegramMessageId: Long) {
        val now = Instant.now(clock)
        jobs.markSucceeded(checkNotNull(job.id), telegramMessageId, now)
        if (job.type == dev.aileadmanager.core.TelegramDeliveryType.MANAGER_APPROVED_REPLY) {
            messages.markDeliverySucceeded(checkNotNull(job.leadMessageId), job.chatId, telegramMessageId)
            drafts.markSent(checkNotNull(job.replyDraftId), now)
            conversations.markAwaitingReply(checkNotNull(job.conversationId), now)
        }
    }
}

@Service
class TelegramDeliveryFailureService(
    private val jobs: TelegramDeliveryJobRepository,
    private val messages: LeadMessageRepository,
    private val drafts: ReplyDraftRepository,
    private val conversations: TelegramConversationRepository,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        job: TelegramDeliveryJob,
        errorCode: TelegramDeliveryErrorCode,
        retryable: Boolean,
        retryAfter: Duration?,
    ) {
        val now = Instant.now(clock)
        val jobId = checkNotNull(job.id)
        val safeMessage = safeMessages.getValue(errorCode)
        if (!retryable || job.attemptCount >= job.maxAttempts) {
            jobs.markFailed(jobId, errorCode, safeMessage, now)
            if (job.type == dev.aileadmanager.core.TelegramDeliveryType.MANAGER_APPROVED_REPLY) {
                messages.markDeliveryFailed(checkNotNull(job.leadMessageId))
                drafts.markFailed(checkNotNull(job.replyDraftId), now)
                conversations.markCancelled(checkNotNull(job.conversationId), now)
            }
            return
        }
        val delay = retryAfter ?: retryDelay(job.attemptCount)
        jobs.reschedule(jobId, now.plus(delay.coerceAtMost(MAX_RETRY_DELAY)), errorCode, safeMessage, now)
    }

    private fun retryDelay(attempt: Int): Duration {
        val baseSeconds = 10.0 * 2.0.pow((attempt - 1).coerceAtLeast(0))
        val jitter = Random.nextDouble(0.8, 1.2)
        return Duration.ofMillis((baseSeconds * jitter * 1000).toLong())
    }

    private companion object {
        val MAX_RETRY_DELAY: Duration = Duration.ofMinutes(30)
        val safeMessages = mapOf(
            TelegramDeliveryErrorCode.NETWORK_ERROR to "Telegram Bot API network request failed",
            TelegramDeliveryErrorCode.RATE_LIMITED to "Telegram Bot API rate limit was reached",
            TelegramDeliveryErrorCode.PROVIDER_UNAVAILABLE to "Telegram Bot API is temporarily unavailable",
            TelegramDeliveryErrorCode.AUTHENTICATION_FAILED to "Telegram Bot API authentication failed",
            TelegramDeliveryErrorCode.CHAT_UNAVAILABLE to "Telegram chat is unavailable",
            TelegramDeliveryErrorCode.INVALID_REQUEST to "Telegram Bot API rejected the message",
            TelegramDeliveryErrorCode.INTERNAL_ERROR to "Internal Telegram delivery error",
        )
    }
}

@Service
class TelegramDeliveryProcessor(
    private val client: TelegramBotClient,
    private val completion: TelegramDeliveryCompletionService,
    private val failures: TelegramDeliveryFailureService,
) {
    fun process(job: TelegramDeliveryJob) {
        try {
            val sent = client.sendMessage(TelegramOutboundMessage(
                chatId = job.chatId,
                text = job.text,
                buttonText = job.buttonText,
                buttonUrl = job.buttonUrl,
            ))
            completion.complete(job, sent.messageId)
        } catch (error: TelegramBotApiException) {
            failures.record(job, error.errorCode, error.retryable, error.retryAfter)
        } catch (error: Exception) {
            log.error("Unexpected Telegram delivery failure for job {}", job.id, error)
            failures.record(job, TelegramDeliveryErrorCode.INTERNAL_ERROR, retryable = true, retryAfter = null)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(TelegramDeliveryProcessor::class.java)
    }
}
