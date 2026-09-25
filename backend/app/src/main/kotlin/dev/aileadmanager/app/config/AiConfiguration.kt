package dev.aileadmanager.app.config

import dev.aileadmanager.ai.StubAiProvider
import dev.aileadmanager.core.AiProvider
import java.time.Clock
import java.util.concurrent.Executor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
@EnableScheduling
class AiConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun aiProvider(@Value("\${ai.provider:stub}") provider: String): AiProvider = when (provider) {
        "stub" -> StubAiProvider()
        else -> error("Unsupported AI provider: $provider")
    }

    @Bean("aiJobExecutor")
    fun aiJobExecutor(@Value("\${ai.worker.concurrency:2}") concurrency: Int): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = concurrency
            maxPoolSize = concurrency
            queueCapacity = concurrency * 2
            setThreadNamePrefix("ai-job-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(20)
            initialize()
        }
}
