package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.AiAnalysisDraft
import dev.aileadmanager.core.AiExtractedFacts
import dev.aileadmanager.core.AiJob
import dev.aileadmanager.core.AiMissingField
import dev.aileadmanager.core.AiPriority
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AiAnalysisValidatorTests {
    private val validator = AiAnalysisValidator()
    private val job = AiJob(id = 3, leadId = 7, nextAttemptAt = Instant.EPOCH)

    @Test
    fun `normalizes and accepts structured analysis`() {
        val result = validator.validate(job, draft())

        assertEquals("Website request", result.summary)
        assertEquals(setOf(AiMissingField.BUDGET), result.missingFields)
        assertEquals("What budget range are you considering?", result.suggestedQuestion)
    }

    @Test
    fun `requires a clarification question for missing fields`() {
        assertFailsWith<InvalidAiAnalysis> {
            validator.validate(job, draft().copy(suggestedQuestion = null))
        }
    }

    @Test
    fun `rejects blank summary`() {
        assertFailsWith<InvalidAiAnalysis> {
            validator.validate(job, draft().copy(summary = "  "))
        }
    }

    @Test
    fun `rejects incomplete extracted budget`() {
        assertFailsWith<InvalidAiAnalysis> {
            validator.validate(job, draft().copy(
                extractedFacts = AiExtractedFacts(category = "website_development", budgetAmount = "1000"),
            ))
        }
    }

    private fun draft() = AiAnalysisDraft(
        summary = "  Website request  ",
        extractedFacts = AiExtractedFacts(category = "website_development"),
        missingFields = setOf(AiMissingField.BUDGET),
        suggestedQuestion = "  What budget range are you considering?  ",
        priority = AiPriority.LOW,
        priorityReason = "No urgent deadline was identified.",
    )
}
