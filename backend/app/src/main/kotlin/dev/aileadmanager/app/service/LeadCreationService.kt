package dev.aileadmanager.app.service

import dev.aileadmanager.core.Lead
import dev.aileadmanager.core.usecase.CreateLeadCommand
import dev.aileadmanager.core.usecase.CreateLeadUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LeadCreationService(private val useCase: CreateLeadUseCase) {
    @Transactional
    fun create(command: CreateLeadCommand): Lead = useCase.create(command)
}
