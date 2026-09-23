package dev.aileadmanager.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository

internal interface SpringUserRepository : CrudRepository<UserRecord, Long> {
    fun findByTelegramUserId(telegramUserId: Long): UserRecord?
}

internal interface SpringServiceCategoryRepository : CrudRepository<ServiceCategoryRecord, Long> {
    fun findByCode(code: String): ServiceCategoryRecord?
    fun findByActiveTrueOrderByNameAsc(): List<ServiceCategoryRecord>
}

internal interface SpringLeadRepository : CrudRepository<LeadRecord, Long>, PagingAndSortingRepository<LeadRecord, Long> {
    fun findByIdAndCustomerId(id: Long, customerId: Long): LeadRecord?
    fun findByCustomerId(customerId: Long, pageable: Pageable): Page<LeadRecord>
}

internal interface SpringLeadEventRepository : CrudRepository<LeadEventRecord, Long> {
    fun findByLeadIdOrderByCreatedAtDescIdDesc(leadId: Long): List<LeadEventRecord>
}
