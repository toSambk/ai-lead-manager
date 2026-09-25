package dev.aileadmanager.app

import dev.aileadmanager.app.service.AiJobClaimService
import dev.aileadmanager.app.service.AiJobProcessor
import dev.aileadmanager.app.service.AiJobFailureService
import dev.aileadmanager.core.AiJobErrorCode
import dev.aileadmanager.core.AiAnalysisResultRepository
import dev.aileadmanager.core.AiJobRepository
import dev.aileadmanager.core.AiJobStatus
import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["ai.worker.enabled=false"])
@Import(PostgresTestConfiguration::class)
class AiPipelineIntegrationTests {
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var categories: ServiceCategoryRepository
    @Autowired private lateinit var leads: LeadRepository
    @Autowired private lateinit var jobs: AiJobRepository
    @Autowired private lateinit var results: AiAnalysisResultRepository
    @Autowired private lateinit var events: LeadEventRepository
    @Autowired private lateinit var claims: AiJobClaimService
    @Autowired private lateinit var processor: AiJobProcessor
    @Autowired private lateinit var failures: AiJobFailureService
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    @Transactional
    fun `worker stores validated stub result and requests clarification`() {
        val customer = users.save(User(
            telegramUserId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
            displayName = "AI pipeline customer",
        ))
        val category = categories.findByCode("automation")!!
        val lead = leads.save(Lead(
            customerId = customer.id!!,
            categoryId = category.id!!,
            description = "Automate appointment reminders",
            contactDetails = "@customer",
        ))
        val queued = jobs.enqueue(lead.id!!, Instant.now())

        val claimed = claims.claim("integration-worker", 1, Duration.ofMinutes(2)).single()
        assertEquals(queued.id, claimed.id)
        assertEquals(1, claimed.attemptCount)
        processor.process(claimed)

        val completed = jobs.findById(claimed.id!!)
        assertEquals(AiJobStatus.SUCCEEDED, completed?.status)
        val result = results.findByJobId(claimed.id!!)
        assertNotNull(result)
        assertTrue(result.missingFields.isNotEmpty())
        assertEquals(LeadStatus.CLARIFICATION, leads.findById(lead.id!!)?.status)
        assertEquals(LeadEventType.STATUS_CHANGED, events.findByLeadId(lead.id!!).single().type)
    }

    @Test
    fun `retryable failure is delayed and fails after the attempt limit`() {
        val telegramId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
        val customer = users.save(User(telegramUserId = telegramId, displayName = "Retry customer"))
        val category = categories.findByCode("automation")!!
        val lead = leads.save(Lead(
            customerId = customer.id!!,
            categoryId = category.id!!,
            description = "Retry test",
            contactDetails = "@customer",
        ))
        try {
            val queued = jobs.enqueue(lead.id!!, Instant.now(), maxAttempts = 2)
            val firstAttempt = claims.claim("retry-worker", 1, Duration.ofMinutes(2)).single()
            failures.record(firstAttempt, AiJobErrorCode.PROVIDER_UNAVAILABLE, retryable = true, retryAfter = Duration.ofSeconds(30))

            val delayed = jobs.findById(queued.id!!)
            assertEquals(AiJobStatus.PENDING, delayed?.status)
            assertEquals(1, delayed?.attemptCount)
            assertEquals(AiJobErrorCode.PROVIDER_UNAVAILABLE, delayed?.lastErrorCode)

            jdbc.update(
                "UPDATE \"AI_LEAD_MANAGER\".ai_jobs SET next_attempt_at = TIMESTAMPTZ '1970-01-01 00:00:00Z' WHERE id = ?",
                queued.id,
            )
            val secondAttempt = claims.claim("retry-worker", 1, Duration.ofMinutes(2)).single()
            failures.record(secondAttempt, AiJobErrorCode.PROVIDER_UNAVAILABLE, retryable = true, retryAfter = null)

            assertEquals(AiJobStatus.FAILED, jobs.findById(queued.id!!)?.status)
            assertEquals(2, jobs.findById(queued.id!!)?.attemptCount)
        } finally {
            jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".leads WHERE id = ?", lead.id)
            jdbc.update("DELETE FROM \"AI_LEAD_MANAGER\".users WHERE id = ?", customer.id)
        }
    }
}
