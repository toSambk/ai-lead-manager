package dev.aileadmanager.persistence

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventType
import dev.aileadmanager.core.LeadMessage
import dev.aileadmanager.core.LeadMessageKind
import dev.aileadmanager.core.LeadStatus
import dev.aileadmanager.core.ServiceCategory
import dev.aileadmanager.core.User
import dev.aileadmanager.core.UserRole
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Table

@Table(value = "users", schema = "AI_LEAD_MANAGER")
internal data class UserRecord(
    @field:Id val id: Long? = null,
    val telegramUserId: Long,
    val role: UserRole,
    val displayName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun toDomain() = User(id, telegramUserId, role, displayName, createdAt, updatedAt)
}

@Table(value = "service_categories", schema = "AI_LEAD_MANAGER")
internal data class ServiceCategoryRecord(
    @field:Id val id: Long? = null,
    val code: String,
    val name: String,
    val active: Boolean,
) {
    fun toDomain() = ServiceCategory(id, code, name, active)
}

@Table(value = "leads", schema = "AI_LEAD_MANAGER")
internal data class LeadRecord(
    @field:Id val id: Long? = null,
    val customerId: Long,
    val categoryId: Long,
    val description: String,
    val estimatedBudgetAmount: BigDecimal?,
    val budgetCurrency: String?,
    val desiredDeadline: LocalDate?,
    val contactDetails: String,
    val status: LeadStatus,
    val ownerId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    @field:Version val version: Long? = null,
) {
    fun toDomain() = Lead(
        id, customerId, categoryId, description, estimatedBudgetAmount, budgetCurrency,
        desiredDeadline, contactDetails, status, ownerId, createdAt, updatedAt, version,
    )
}

@Table(value = "lead_events", schema = "AI_LEAD_MANAGER")
internal data class LeadEventRecord(
    @field:Id val id: Long? = null,
    val leadId: Long,
    val actorId: Long?,
    val eventType: LeadEventType,
    val oldStatus: LeadStatus?,
    val newStatus: LeadStatus?,
    val oldOwnerId: Long?,
    val newOwnerId: Long?,
    val createdAt: Instant,
) {
    fun toDomain() = LeadEvent(
        id = id,
        leadId = leadId,
        actorId = actorId,
        type = eventType,
        oldStatus = oldStatus,
        newStatus = newStatus,
        oldOwnerId = oldOwnerId,
        newOwnerId = newOwnerId,
        createdAt = createdAt,
    )
}

@Table(value = "lead_messages", schema = "AI_LEAD_MANAGER")
internal data class LeadMessageRecord(
    @field:Id val id: Long? = null,
    val leadId: Long,
    val senderId: Long?,
    val kind: LeadMessageKind,
    val body: String,
    val createdAt: Instant,
) {
    fun toDomain() = LeadMessage(
        id = id,
        leadId = leadId,
        senderId = senderId,
        kind = kind,
        body = body,
        createdAt = createdAt,
    )
}
