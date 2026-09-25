package dev.aileadmanager.persistence

import dev.aileadmanager.core.AiAnalysisResult
import dev.aileadmanager.core.AiAnalysisResultRepository
import dev.aileadmanager.core.AiExtractedFacts
import dev.aileadmanager.core.AiJob
import dev.aileadmanager.core.AiJobErrorCode
import dev.aileadmanager.core.AiJobRepository
import dev.aileadmanager.core.AiJobStatus
import dev.aileadmanager.core.AiMissingField
import dev.aileadmanager.core.AiPriority
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.databind.ObjectMapper

@Repository
internal class JdbcAiJobRepository(private val jdbc: JdbcTemplate) : AiJobRepository {
    override fun enqueue(leadId: Long, now: Instant, maxAttempts: Int): AiJob =
        requireNotNull(jdbc.queryForObject(
            """
            INSERT INTO "AI_LEAD_MANAGER".ai_jobs (lead_id, status, attempt_count, max_attempts, next_attempt_at, created_at, updated_at)
            VALUES (?, 'PENDING', 0, ?, ?, ?, ?)
            RETURNING *
            """.trimIndent(),
            ::mapJob,
            leadId,
            maxAttempts,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now),
        ))

    override fun findById(id: Long): AiJob? = queryOne("id = ?", id)

    override fun findLatestByLeadId(leadId: Long): AiJob? = jdbc.query(
        """
        SELECT * FROM "AI_LEAD_MANAGER".ai_jobs
        WHERE lead_id = ?
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """.trimIndent(),
        ::mapJob,
        leadId,
    ).firstOrNull()

    override fun claimAvailable(workerId: String, now: Instant, lockedUntil: Instant, limit: Int): List<AiJob> = jdbc.query(
        """
        WITH candidates AS (
            SELECT id
            FROM "AI_LEAD_MANAGER".ai_jobs
            WHERE status = 'PENDING'
              AND next_attempt_at <= ?
              AND attempt_count < max_attempts
            ORDER BY next_attempt_at, created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT ?
        )
        UPDATE "AI_LEAD_MANAGER".ai_jobs job
        SET status = 'RUNNING',
            attempt_count = attempt_count + 1,
            locked_at = ?,
            locked_until = ?,
            locked_by = ?,
            updated_at = ?
        FROM candidates
        WHERE job.id = candidates.id
        RETURNING job.*
        """.trimIndent(),
        ::mapJob,
        Timestamp.from(now),
        limit,
        Timestamp.from(now),
        Timestamp.from(lockedUntil),
        workerId,
        Timestamp.from(now),
    )

    override fun markSucceeded(jobId: Long, now: Instant) {
        updateRunning(jobId, """
            status = 'SUCCEEDED', locked_at = NULL, locked_until = NULL, locked_by = NULL,
            last_error_code = NULL, last_error_message = NULL, updated_at = ?
        """.trimIndent(), Timestamp.from(now))
    }

    override fun reschedule(
        jobId: Long,
        nextAttemptAt: Instant,
        errorCode: AiJobErrorCode,
        errorMessage: String,
        now: Instant,
    ) {
        updateRunning(
            jobId,
            """
            status = 'PENDING', next_attempt_at = ?, locked_at = NULL, locked_until = NULL, locked_by = NULL,
            last_error_code = ?, last_error_message = ?, updated_at = ?
            """.trimIndent(),
            Timestamp.from(nextAttemptAt),
            errorCode.name,
            errorMessage.take(1000),
            Timestamp.from(now),
        )
    }

    override fun markFailed(jobId: Long, errorCode: AiJobErrorCode, errorMessage: String, now: Instant) {
        updateRunning(
            jobId,
            """
            status = 'FAILED', locked_at = NULL, locked_until = NULL, locked_by = NULL,
            last_error_code = ?, last_error_message = ?, updated_at = ?
            """.trimIndent(),
            errorCode.name,
            errorMessage.take(1000),
            Timestamp.from(now),
        )
    }

    override fun recoverStale(now: Instant): Int = jdbc.update(
        """
        UPDATE "AI_LEAD_MANAGER".ai_jobs
        SET status = CASE WHEN attempt_count >= max_attempts THEN 'FAILED' ELSE 'PENDING' END,
            next_attempt_at = ?,
            locked_at = NULL,
            locked_until = NULL,
            locked_by = NULL,
            last_error_code = 'INTERNAL_ERROR',
            last_error_message = 'Worker lease expired before completion',
            updated_at = ?
        WHERE status = 'RUNNING' AND locked_until < ?
        """.trimIndent(),
        Timestamp.from(now),
        Timestamp.from(now),
        Timestamp.from(now),
    )

    private fun queryOne(predicate: String, value: Any): AiJob? = jdbc.query(
        "SELECT * FROM \"AI_LEAD_MANAGER\".ai_jobs WHERE $predicate",
        ::mapJob,
        value,
    ).firstOrNull()

    private fun updateRunning(jobId: Long, assignments: String, vararg parameters: Any?) {
        val arguments = parameters.toMutableList().apply { add(jobId) }.toTypedArray()
        val changed = jdbc.update(
            "UPDATE \"AI_LEAD_MANAGER\".ai_jobs SET $assignments WHERE id = ? AND status = 'RUNNING'",
            *arguments,
        )
        check(changed == 1) { "AI job $jobId is not running" }
    }

    private fun mapJob(result: ResultSet, row: Int) = AiJob(
        id = result.getLong("id"),
        leadId = result.getLong("lead_id"),
        status = AiJobStatus.valueOf(result.getString("status")),
        attemptCount = result.getInt("attempt_count"),
        maxAttempts = result.getInt("max_attempts"),
        nextAttemptAt = result.getTimestamp("next_attempt_at").toInstant(),
        lockedAt = result.getTimestamp("locked_at")?.toInstant(),
        lockedUntil = result.getTimestamp("locked_until")?.toInstant(),
        lockedBy = result.getString("locked_by"),
        lastErrorCode = result.getString("last_error_code")?.let(AiJobErrorCode::valueOf),
        lastErrorMessage = result.getString("last_error_message"),
        createdAt = result.getTimestamp("created_at").toInstant(),
        updatedAt = result.getTimestamp("updated_at").toInstant(),
    )
}

@Repository
internal class JdbcAiAnalysisResultRepository(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) : AiAnalysisResultRepository {
    override fun save(result: AiAnalysisResult): AiAnalysisResult {
        val factsJson = mapper.writeValueAsString(result.extractedFacts)
        val missingJson = mapper.writeValueAsString(result.missingFields.map { it.name })
        val saved = jdbc.query(
            """
            INSERT INTO "AI_LEAD_MANAGER".ai_results (
                job_id, lead_id, summary, extracted_facts, missing_fields,
                suggested_question, priority, priority_reason
            ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
            ON CONFLICT (job_id) DO NOTHING
            RETURNING *
            """.trimIndent(),
            ::mapResult,
            result.jobId,
            result.leadId,
            result.summary,
            factsJson,
            missingJson,
            result.suggestedQuestion,
            result.priority.name,
            result.priorityReason,
        ).firstOrNull()
        return saved ?: requireNotNull(findByJobId(result.jobId))
    }

    override fun findByJobId(jobId: Long): AiAnalysisResult? = query(
        "WHERE job_id = ? LIMIT 1",
        jobId,
    )

    override fun findLatestByLeadId(leadId: Long): AiAnalysisResult? = query(
        "WHERE lead_id = ? ORDER BY created_at DESC, id DESC LIMIT 1",
        leadId,
    )

    private fun query(suffix: String, value: Long): AiAnalysisResult? = jdbc.query(
        "SELECT * FROM \"AI_LEAD_MANAGER\".ai_results $suffix",
        ::mapResult,
        value,
    ).firstOrNull()

    private fun mapResult(result: ResultSet, row: Int): AiAnalysisResult {
        val facts = mapper.readTree(result.getString("extracted_facts"))
        val missingNode = mapper.readTree(result.getString("missing_fields"))
        val missing = (0 until missingNode.size())
            .map { index -> AiMissingField.valueOf(missingNode.get(index).stringValue()) }
            .toSet()
        return AiAnalysisResult(
            id = result.getLong("id"),
            jobId = result.getLong("job_id"),
            leadId = result.getLong("lead_id"),
            summary = result.getString("summary"),
            extractedFacts = AiExtractedFacts(
                category = facts.path("category").stringValue(),
                budgetAmount = facts.path("budgetAmount").takeUnless { it.isMissingNode || it.isNull }?.stringValue(),
                budgetCurrency = facts.path("budgetCurrency").takeUnless { it.isMissingNode || it.isNull }?.stringValue(),
                desiredDeadline = facts.path("desiredDeadline").takeUnless { it.isMissingNode || it.isNull }?.stringValue(),
            ),
            missingFields = missing,
            suggestedQuestion = result.getString("suggested_question"),
            priority = AiPriority.valueOf(result.getString("priority")),
            priorityReason = result.getString("priority_reason"),
            createdAt = result.getTimestamp("created_at").toInstant(),
        )
    }
}
