package dev.aileadmanager.ai

import dev.aileadmanager.core.AiProvider
import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AiProviderConfiguration {
    @Bean
    @ConditionalOnProperty(name = ["ai.provider"], havingValue = "stub", matchIfMissing = true)
    fun stubAiProvider(): AiProvider = StubAiProvider()

    @Bean
    @ConditionalOnProperty(name = ["ai.provider"], havingValue = "openai")
    fun openAiProvider(chatClientBuilder: ChatClient.Builder): AiProvider =
        OpenAiProvider(chatClientBuilder.build())
}
