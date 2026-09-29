package dev.aileadmanager.ai

import dev.aileadmanager.core.AiAnalysisInput
import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.ServiceCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StubAiProviderTests {
    @Test
    fun `customer clarification fills budget and deadline`() {
        val result = StubAiProvider().analyze(AiAnalysisInput(
            lead = Lead(
                id = 1,
                customerId = 2,
                categoryId = 3,
                description = "Automate appointment reminders",
                contactDetails = "@customer",
            ),
            category = ServiceCategory(id = 3, code = "automation", name = "Automation", active = true),
            conversation = listOf(
                LeadMessage(
                    id = 4,
                    leadId = 1,
                    senderId = 2,
                    kind = LeadMessageKind.CUSTOMER_REPLY,
                    body = "The budget is 1500 USD and the deadline is 2026-12-01.",
                ),
            ),
        ))

        assertEquals("1500", result.extractedFacts.budgetAmount)
        assertEquals("USD", result.extractedFacts.budgetCurrency)
        assertEquals("2026-12-01", result.extractedFacts.desiredDeadline)
        assertTrue(result.missingFields.isEmpty())
        assertEquals(null, result.suggestedQuestion)
    }
}
