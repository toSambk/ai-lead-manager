package dev.aileadmanager.persistence

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.ServiceCategory
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

@Repository
internal class JdbcUserRepository(private val records: SpringUserRepository) : UserRepository {
    override fun save(user: User): User {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        return records.save(UserRecord(
            id = user.id,
            telegramUserId = user.telegramUserId,
            role = user.role,
            displayName = user.displayName,
            createdAt = user.createdAt ?: now,
            updatedAt = now,
        )).toDomain()
    }

    override fun findById(id: Long): User? = records.findById(id).orElse(null)?.toDomain()

    override fun findByTelegramUserId(telegramUserId: Long): User? =
        records.findByTelegramUserId(telegramUserId)?.toDomain()
}

@Repository
internal class JdbcServiceCategoryRepository(
    private val records: SpringServiceCategoryRepository,
) : ServiceCategoryRepository {
    override fun save(category: ServiceCategory): ServiceCategory = records.save(ServiceCategoryRecord(
        id = category.id,
        code = category.code,
        name = category.name,
        active = category.active,
    )).toDomain()

    override fun findById(id: Long): ServiceCategory? = records.findById(id).orElse(null)?.toDomain()

    override fun findByCode(code: String): ServiceCategory? = records.findByCode(code)?.toDomain()

    override fun findActive(): List<ServiceCategory> =
        records.findByActiveTrueOrderByNameAsc().map(ServiceCategoryRecord::toDomain)
}

@Repository
internal class JdbcLeadRepository(private val records: SpringLeadRepository) : LeadRepository {
    override fun save(lead: Lead): Lead {
        val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
        return records.save(LeadRecord(
            id = lead.id,
            customerId = lead.customerId,
            categoryId = lead.categoryId,
            description = lead.description,
            estimatedBudgetAmount = lead.estimatedBudgetAmount,
            budgetCurrency = lead.budgetCurrency,
            desiredDeadline = lead.desiredDeadline,
            contactDetails = lead.contactDetails,
            status = lead.status,
            ownerId = lead.ownerId,
            createdAt = lead.createdAt ?: now,
            updatedAt = now,
            version = lead.version,
        )).toDomain()
    }

    override fun findById(id: Long): Lead? = records.findById(id).orElse(null)?.toDomain()

    override fun findByIdForCustomer(id: Long, customerId: Long): Lead? =
        records.findByIdAndCustomerId(id, customerId)?.toDomain()

    override fun findByCustomerId(customerId: Long, page: Int, size: Int): LeadPage =
        records.findByCustomerId(customerId, pageRequest(page, size)).let { result ->
            LeadPage(result.content.map(LeadRecord::toDomain), result.totalElements)
        }

    override fun findAll(page: Int, size: Int): LeadPage =
        records.findAll(pageRequest(page, size)).let { result ->
            LeadPage(result.content.map(LeadRecord::toDomain), result.totalElements)
        }

    private fun pageRequest(page: Int, size: Int): PageRequest =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
}
