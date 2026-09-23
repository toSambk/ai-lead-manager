package dev.aileadmanager.app.config

import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.LeadEventRepository
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.usecase.CreateLeadUseCase
import dev.aileadmanager.core.usecase.ChangeLeadStatusUseCase
import dev.aileadmanager.core.usecase.GetLeadUseCase
import dev.aileadmanager.core.usecase.ListLeadEventsUseCase
import dev.aileadmanager.core.usecase.ListLeadsUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfiguration {
    @Bean
    fun createLeadUseCase(
        users: UserRepository,
        categories: ServiceCategoryRepository,
        leads: LeadRepository,
    ) = CreateLeadUseCase(users, categories, leads)

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
    fun listLeadEventsUseCase(
        leads: LeadRepository,
        events: LeadEventRepository,
    ) = ListLeadEventsUseCase(leads, events)
}
