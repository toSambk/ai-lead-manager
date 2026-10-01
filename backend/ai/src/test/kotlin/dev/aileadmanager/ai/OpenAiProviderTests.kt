package dev.aileadmanager.ai

import dev.aileadmanager.core.AiAnalysisInput
import dev.aileadmanager.core.AiProvider
import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.ServiceCategory
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class OpenAiProviderTests {
    @Test
    fun `stub provider is selected by default without an OpenAI client`() {
        AnnotationConfigApplicationContext().use { context ->
            context.register(AiProviderConfiguration::class.java)
            context.refresh()

            assertTrue(context.getBean(AiProvider::class.java) is StubAiProvider)
        }
    }

    @Test
    fun `structured response schema can be generated`() {
        val schema = BeanOutputConverter(OpenAiAnalysisResponse::class.java).jsonSchema

        assertContains(schema, "summary")
        assertContains(schema, "extractedFacts")
        assertContains(schema, "missingFields")
        assertContains(schema, "suggestedQuestion")
        assertContains(schema, "priorityReason")
    }

    @Test
    fun `prompt includes analysis fields and excludes customer contact details`() {
        val input = AiAnalysisInput(
            lead = Lead(
                id = 1,
                customerId = 2,
                categoryId = 3,
                description = "Build a booking website",
                estimatedBudgetAmount = BigDecimal("2500.00"),
                budgetCurrency = "USD",
                desiredDeadline = LocalDate.parse("2026-12-01"),
                contactDetails = "private@example.com",
            ),
            category = ServiceCategory(id = 3, code = "website", name = "Website development", active = true),
            conversation = listOf(
                LeadMessage(
                    id = 4,
                    leadId = 1,
                    senderId = 2,
                    kind = LeadMessageKind.CUSTOMER_REPLY,
                    body = "It needs online payments.",
                ),
            ),
        )

        val prompt = buildOpenAiUserPrompt(input)

        assertContains(prompt, "Build a booking website")
        assertContains(prompt, "2500.00")
        assertContains(prompt, "2026-12-01")
        assertContains(prompt, "It needs online payments.")
        assertFalse(prompt.contains("private@example.com"))
    }
}
