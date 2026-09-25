package dev.aileadmanager.app.config

import dev.aileadmanager.app.security.DevAuthenticationService
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("local")
class DevAuthenticationConfiguration {
    @Bean
    fun devUserSeeder(devAuthentication: DevAuthenticationService) = ApplicationRunner {
        devAuthentication.seedUsers()
    }
}
