package dev.aileadmanager.core.usecase

import dev.aileadmanager.core.LeadEvent
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.UserRole

class ListLeadEventsUseCase(
    private val leads: LeadRepository,
    private val events: LeadEventRepository,
) {
    fun list(leadId: Long, reader: LeadReader): List<LeadEvent> {
        if (reader.role == UserRole.CUSTOMER) {
            throw ChangeLeadStatusException(ChangeLeadStatusFailure.FORBIDDEN, "Lead events are available only to managers")
        }
        if (leads.findById(leadId) == null) {
            throw ChangeLeadStatusException(ChangeLeadStatusFailure.LEAD_NOT_FOUND, "Lead not found")
        }
        return events.findByLeadId(leadId)
    }
}
