package dev.aileadmanager.app.config

import dev.aileadmanager.app.security.SessionUserFilter
import dev.aileadmanager.core.UserRepository
import dev.aileadmanager.core.UserRole.CUSTOMER
import dev.aileadmanager.telegram.TelegramInitDataVerifier
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository

@Configuration
class SecurityConfiguration {
    @Bean
    fun csrfTokenRepository(): CsrfTokenRepository = HttpSessionCsrfTokenRepository()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        users: UserRepository,
        csrfTokenRepository: CsrfTokenRepository,
    ): SecurityFilterChain {
        http.csrf { csrf ->
            csrf.csrfTokenRepository(csrfTokenRepository)
            csrf.ignoringRequestMatchers("/api/telegram/webhook")
        }
        http.authorizeHttpRequests { rules ->
            rules.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            rules.requestMatchers(HttpMethod.GET, "/api/system", "/actuator/health", "/api/auth/csrf").permitAll()
            rules.requestMatchers(HttpMethod.POST, "/api/auth/telegram").permitAll()
            rules.requestMatchers("/api/telegram/webhook").permitAll()
            rules.requestMatchers("/api/admin/**").hasRole("ADMIN")
            rules.requestMatchers(HttpMethod.POST, "/api/leads").hasRole(CUSTOMER.name)
            rules.anyRequest().authenticated()
        }
        http.httpBasic { it.disable() }
        http.formLogin { it.disable() }
        http.logout { it.disable() }
        http.exceptionHandling { exceptions ->
            exceptions.authenticationEntryPoint { _, response, _ -> response.sendError(401) }
            exceptions.accessDeniedHandler { _, response, _ -> response.sendError(403) }
        }
        http.addFilterBefore(SessionUserFilter(users), AnonymousAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun telegramInitDataVerifier(@Value("\${TELEGRAM_BOT_TOKEN:}") botToken: String) =
        TelegramInitDataVerifier(botToken)

}
