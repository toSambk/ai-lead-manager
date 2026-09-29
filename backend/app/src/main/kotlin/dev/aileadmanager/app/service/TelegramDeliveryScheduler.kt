package dev.aileadmanager.app.service

import java.net.InetAddress
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import kotlin.math.min
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "telegram.delivery.worker",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TelegramDeliveryScheduler(
    private val claims: TelegramDeliveryClaimService,
    private val processor: TelegramDeliveryProcessor,
    @Qualifier("telegramDeliveryExecutor") private val executor: Executor,
    @Value("\${telegram.delivery.worker.batch-size:5}") private val batchSize: Int,
    @Value("\${telegram.delivery.worker.lock-timeout:PT2M}") private val lockTimeout: Duration,
    @Value("\${telegram.delivery.worker.concurrency:2}") concurrency: Int,
) {
    private val workerId = "${hostName()}-${UUID.randomUUID()}"
    private val capacity = Semaphore(concurrency)

    @Scheduled(fixedDelayString = "\${telegram.delivery.worker.poll-delay:1000}")
    fun processAvailableJobs() {
        val available = min(batchSize, capacity.availablePermits())
        if (available == 0) return
        capacity.acquire(available)
        val jobs = try {
            claims.claim(workerId, available, lockTimeout)
        } catch (error: Exception) {
            capacity.release(available)
            throw error
        }
        capacity.release(available - jobs.size)
        jobs.forEach { job ->
            executor.execute {
                try {
                    processor.process(job)
                } finally {
                    capacity.release()
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "\${telegram.delivery.worker.recovery-delay:60000}")
    fun recoverStaleJobs() {
        val recovered = claims.recoverStale()
        if (recovered > 0) log.warn("Recovered {} stale Telegram delivery jobs", recovered)
    }

    private fun hostName(): String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("worker")

    private companion object {
        val log = LoggerFactory.getLogger(TelegramDeliveryScheduler::class.java)
    }
}
