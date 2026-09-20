package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ServiceCategory
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.UserRole
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CreateLeadUseCaseTests {
    private val users = FakeUsers()
    private val categories = FakeCategories()
    private val leads = FakeLeads()
    private val useCase = CreateLeadUseCase(users, categories, leads)

    @Test
    fun `creates a new lead for a customer and active category`() {
        val created = useCase.create(command(
            description = "  Build a website  ",
            contactDetails = "  @customer  ",
            estimatedBudgetAmount = BigDecimal("1200.50"),
            budgetCurrency = " usd ",
        ))

        assertEquals(42L, created.id)
        assertEquals(LeadStatus.NEW, created.status)
        assertEquals(7L, created.customerId)
        assertEquals(3L, created.categoryId)
        assertEquals("Build a website", created.description)
        assertEquals("@customer", created.contactDetails)
        assertEquals("USD", created.budgetCurrency)
        assertNull(created.ownerId)
        assertEquals(created, leads.saved)
    }

    @Test
    fun `rejects a category that is inactive`() {
        categories.category = categories.category.copy(active = false)

        val error = assertFailsWith<CreateLeadException> { useCase.create(command()) }

        assertEquals(CreateLeadFailure.CATEGORY_UNAVAILABLE, error.failure)
        assertNull(leads.saved)
    }

    @Test
    fun `rejects a manager as the customer`() {
        users.user = users.user.copy(role = UserRole.MANAGER)

        val error = assertFailsWith<CreateLeadException> { useCase.create(command()) }

        assertEquals(CreateLeadFailure.CUSTOMER_UNAVAILABLE, error.failure)
        assertNull(leads.saved)
    }

    @Test
    fun `rejects a budget without currency`() {
        val error = assertFailsWith<CreateLeadException> {
            useCase.create(command(estimatedBudgetAmount = BigDecimal("100")))
        }

        assertEquals(CreateLeadFailure.INVALID_INPUT, error.failure)
        assertNull(leads.saved)
    }

    @Test
    fun `rejects a budget outside database precision`() {
        val error = assertFailsWith<CreateLeadException> {
            useCase.create(command(
                estimatedBudgetAmount = BigDecimal("1000000000000.00"),
                budgetCurrency = "USD",
            ))
        }

        assertEquals(CreateLeadFailure.INVALID_INPUT, error.failure)
        assertNull(leads.saved)
    }

    private fun command(
        description: String = "Build a website",
        contactDetails: String = "@customer",
        estimatedBudgetAmount: BigDecimal? = null,
        budgetCurrency: String? = null,
    ) = CreateLeadCommand(
        customerId = 7,
        categoryId = 3,
        description = description,
        contactDetails = contactDetails,
        estimatedBudgetAmount = estimatedBudgetAmount,
        budgetCurrency = budgetCurrency,
    )

    private class FakeUsers : UserRepository {
        var user = User(id = 7, telegramUserId = 123, displayName = "Customer")
        override fun save(user: User) = user
        override fun upsertTelegramProfile(telegramUserId: Long, displayName: String) = user
        override fun findById(id: Long) = user.takeIf { it.id == id }
        override fun findByTelegramUserId(telegramUserId: Long) = user.takeIf { it.telegramUserId == telegramUserId }
    }

    private class FakeCategories : ServiceCategoryRepository {
        var category = ServiceCategory(id = 3, code = "website_development", name = "Website development")
        override fun save(category: ServiceCategory) = category
        override fun findById(id: Long) = category.takeIf { it.id == id }
        override fun findByCode(code: String) = category.takeIf { it.code == code }
        override fun findActive() = listOf(category).filter { it.active }
    }

    private class FakeLeads : LeadRepository {
        var saved: Lead? = null
        override fun save(lead: Lead): Lead = lead.copy(id = 42).also { saved = it }
        override fun findById(id: Long): Lead? = null
        override fun findByIdForCustomer(id: Long, customerId: Long): Lead? = null
        override fun findByCustomerId(customerId: Long, page: Int, size: Int) = LeadPage(emptyList(), 0)
        override fun findAll(page: Int, size: Int) = LeadPage(emptyList(), 0)
    }
}
