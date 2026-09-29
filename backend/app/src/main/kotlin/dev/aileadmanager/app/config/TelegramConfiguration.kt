package dev.aileadmanager.app.config

import dev.aileadmanager.telegram.HttpTelegramBotClient
import dev.aileadmanager.telegram.TelegramBotClient
import dev.aileadmanager.telegram.TelegramUpdateParser
import dev.aileadmanager.telegram.TelegramWebhookSecretVerifier
import java.net.URI
import java.util.concurrent.Executor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

data class TelegramRuntimeSettings(
    val miniAppUrl: String?,
    val managerChatId: Long?,
)

@Configuration
class TelegramConfiguration {
    @Bean
    fun telegramUpdateParser() = TelegramUpdateParser()

    @Bean
    fun telegramWebhookSecretVerifier(
        @Value("\${telegram.webhook-secret:}") secret: String,
    ) = TelegramWebhookSecretVerifier(secret)

    @Bean
    fun telegramBotClient(
        @Value("\${TELEGRAM_BOT_TOKEN:}") token: String,
        @Value("\${telegram.api-base-url:https://api.telegram.org}") apiBaseUrl: String,
    ): TelegramBotClient = HttpTelegramBotClient(token, apiBaseUrl)

    @Bean
    fun telegramRuntimeSettings(
        @Value("\${telegram.mini-app-url:}") miniAppUrl: String,
        @Value("\${telegram.manager-chat-id:}") managerChatId: String,
    ): TelegramRuntimeSettings {
        val parsedUrl = miniAppUrl.trim().takeIf(String::isNotEmpty)?.also(::validateMiniAppUrl)
        val parsedChatId = managerChatId.trim().takeIf(String::isNotEmpty)?.toLongOrNull()
        require(managerChatId.isBlank() || parsedChatId != null) { "telegram.manager-chat-id must be a number" }
        return TelegramRuntimeSettings(parsedUrl, parsedChatId)
    }

    @Bean("telegramDeliveryExecutor")
    fun telegramDeliveryExecutor(
        @Value("\${telegram.delivery.worker.concurrency:2}") concurrency: Int,
    ): Executor = ThreadPoolTaskExecutor().apply {
        corePoolSize = concurrency
        maxPoolSize = concurrency
        queueCapacity = concurrency * 2
        setThreadNamePrefix("telegram-delivery-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(20)
        initialize()
    }

    private fun validateMiniAppUrl(value: String) {
        val uri = runCatching { URI.create(value) }.getOrElse {
            throw IllegalArgumentException("telegram.mini-app-url must be a valid URL")
        }
        require(uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOCAL_HOSTS)) {
            "telegram.mini-app-url must use HTTPS except on localhost"
        }
        require(!uri.host.isNullOrBlank()) { "telegram.mini-app-url must contain a host" }
    }

    private companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1")
    }
}
