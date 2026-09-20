package dev.aileadmanager.app

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.UserRole
import java.util.concurrent.ThreadLocalRandom
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Import(PostgresTestConfiguration::class)
class PersistenceIntegrationTests {
    @Autowired lateinit var users: UserRepository
    @Autowired lateinit var categories: ServiceCategoryRepository
    @Autowired lateinit var leads: LeadRepository

    @Test
    @Transactional
    fun `spring data repositories create and retrieve a lead`() {
        val category = categories.findByCode("automation")!!
        assertTrue(categories.findActive().any { it.id == category.id })

        val telegramId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
        val customer = users.save(User(telegramUserId = telegramId, displayName = "Repository test"))
        assertNotNull(customer.id)
        assertEquals(customer, users.findByTelegramUserId(telegramId))
        val promoted = users.save(customer.copy(role = UserRole.MANAGER))
        val refreshed = users.upsertTelegramProfile(telegramId, "Updated display name")
        assertEquals(promoted.id, refreshed.id)
        assertEquals(UserRole.MANAGER, refreshed.role)
        assertEquals("Updated display name", refreshed.displayName)
        val customerId = customer.id!!

        val lead = leads.save(Lead(
            customerId = customerId,
            categoryId = category.id!!,
            description = "Automate appointment reminders",
            contactDetails = "Telegram",
        ))
        assertNotNull(lead.id)
        val leadId = lead.id!!
        assertEquals(lead, leads.findById(leadId))
        assertEquals(lead, leads.findByIdForCustomer(leadId, customerId))
        assertNull(leads.findByIdForCustomer(leadId, -1))
        assertTrue(leads.findByCustomerId(customerId, 0, 10).items.any { it.id == leadId })
        assertTrue(leads.findAll(0, 10).items.any { it.id == lead.id })

        val updated = leads.save(lead.copy(description = "Automate booking reminders"))
        assertEquals("Automate booking reminders", leads.findById(leadId)?.description)
        assertTrue(updated.version!! > lead.version!!)
    }
}
