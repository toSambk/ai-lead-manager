package dev.aileadmanager.app.config

import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.AiAnalysisResultRepository
import dev.aileadmanager.core.AiJobRepository
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.LeadMessageRepository
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.usecase.CreateLeadUseCase
import dev.aileadmanager.core.usecase.AssignLeadOwnerUseCase
import dev.aileadmanager.core.usecase.ChangeLeadStatusUseCase
import dev.aileadmanager.core.usecase.GetLeadUseCase
import dev.aileadmanager.core.usecase.ListLeadEventsUseCase
import dev.aileadmanager.core.usecase.ListLeadsUseCase
import dev.aileadmanager.core.usecase.ListAssignableManagersUseCase
import dev.aileadmanager.core.usecase.AddLeadNoteUseCase
import dev.aileadmanager.core.usecase.ListLeadNotesUseCase
import dev.aileadmanager.core.usecase.AiAnalysisValidator
import dev.aileadmanager.core.usecase.GetLeadAiAnalysisUseCase
import dev.aileadmanager.core.usecase.RetryLeadAiAnalysisUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfiguration {
    @Bean
    fun createLeadUseCase(
        users: UserRepository,
        categories: ServiceCategoryRepository,
        leads: LeadRepository,
        aiJobs: AiJobRepository,
    ) = CreateLeadUseCase(users, categories, leads, aiJobs)

    @Bean
    fun getLeadUseCase(leads: LeadRepository) = GetLeadUseCase(leads)

    @Bean
    fun listLeadsUseCase(leads: LeadRepository) = ListLeadsUseCase(leads)

    @Bean
    fun changeLeadStatusUseCase(
        leads: LeadRepository,
        events: LeadEventRepository,
    ) = ChangeLeadStatusUseCase(leads, events)

    @Bean
    fun assignLeadOwnerUseCase(
        leads: LeadRepository,
        users: UserRepository,
        events: LeadEventRepository,
    ) = AssignLeadOwnerUseCase(leads, users, events)

    @Bean
    fun listAssignableManagersUseCase(users: UserRepository) = ListAssignableManagersUseCase(users)

    @Bean
    fun listLeadEventsUseCase(
        leads: LeadRepository,
        events: LeadEventRepository,
    ) = ListLeadEventsUseCase(leads, events)

    @Bean
    fun addLeadNoteUseCase(
        leads: LeadRepository,
        messages: LeadMessageRepository,
    ) = AddLeadNoteUseCase(leads, messages)

    @Bean
    fun listLeadNotesUseCase(
        leads: LeadRepository,
        messages: LeadMessageRepository,
    ) = ListLeadNotesUseCase(leads, messages)

    @Bean
    fun aiAnalysisValidator() = AiAnalysisValidator()

    @Bean
    fun getLeadAiAnalysisUseCase(
        leads: LeadRepository,
        jobs: AiJobRepository,
        results: AiAnalysisResultRepository,
    ) = GetLeadAiAnalysisUseCase(leads, jobs, results)

    @Bean
    fun retryLeadAiAnalysisUseCase(
        leads: LeadRepository,
        jobs: AiJobRepository,
    ) = RetryLeadAiAnalysisUseCase(leads, jobs)
}
