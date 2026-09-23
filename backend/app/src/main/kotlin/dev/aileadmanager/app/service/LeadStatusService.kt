package dev.aileadmanager.app.service

import dev.aileadmanager.core.usecase.ChangeLeadStatusCommand
import dev.aileadmanager.core.usecase.ChangeLeadStatusException
import dev.aileadmanager.core.usecase.ChangeLeadStatusFailure
import dev.aileadmanager.core.usecase.ChangeLeadStatusResult
import dev.aileadmanager.core.usecase.ChangeLeadStatusUseCase
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeadStatusService(private val useCase: ChangeLeadStatusUseCase) {
    @Transactional
    fun change(command: ChangeLeadStatusCommand): ChangeLeadStatusResult = try {
        useCase.change(command)
    } catch (error: OptimisticLockingFailureException) {
        throw ChangeLeadStatusException(
            ChangeLeadStatusFailure.VERSION_CONFLICT,
            "Lead was changed by another operation",
            error,
        )
    }
}
