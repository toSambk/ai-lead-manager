package dev.aileadmanager.app.config

import dev.aileadmanager.core.LeadRepository
import dev.aileadmanager.core.ServiceCategoryRepository
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.usecase.CreateLeadUseCase
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
}
