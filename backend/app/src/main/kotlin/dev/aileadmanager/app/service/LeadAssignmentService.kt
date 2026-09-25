package dev.aileadmanager.app.service

import dev.aileadmanager.core.usecase.AssignLeadOwnerCommand
import dev.aileadmanager.core.usecase.AssignLeadOwnerException
import dev.aileadmanager.core.usecase.AssignLeadOwnerFailure
import dev.aileadmanager.core.usecase.AssignLeadOwnerResult
import dev.aileadmanager.core.usecase.AssignLeadOwnerUseCase
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeadAssignmentService(private val useCase: AssignLeadOwnerUseCase) {
    @Transactional
    fun assign(command: AssignLeadOwnerCommand): AssignLeadOwnerResult = try {
        useCase.assign(command)
    } catch (error: OptimisticLockingFailureException) {
        throw AssignLeadOwnerException(
            AssignLeadOwnerFailure.VERSION_CONFLICT,
            "Lead was changed by another operation",
            error,
        )
    }
}
