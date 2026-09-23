package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadPage
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.UserRole

data class LeadReader(
    val userId: Long,
    val role: UserRole,
)

class GetLeadUseCase(private val leads: LeadRepository) {
    fun get(id: Long, reader: LeadReader): Lead? {
        if (id <= 0) return null
        return when (reader.role) {
            UserRole.CUSTOMER -> leads.findByIdForCustomer(id, reader.userId)
            UserRole.MANAGER, UserRole.ADMIN -> leads.findById(id)
        }
    }
}

class ListLeadsUseCase(private val leads: LeadRepository) {
    fun list(reader: LeadReader, page: Int, size: Int): LeadPage {
        require(page >= 0) { "Page must not be negative" }
        require(size in 1..100) { "Page size must be between 1 and 100" }
        return when (reader.role) {
            UserRole.CUSTOMER -> leads.findByCustomerId(reader.userId, page, size)
            UserRole.MANAGER, UserRole.ADMIN -> leads.findAll(page, size)
        }
    }
}
