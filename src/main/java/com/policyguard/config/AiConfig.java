package com.policyguard.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires Spring AI OpenAI-compatible beans from {@code policyguard.*} properties.
 * <p>
 * Two separate {@link OpenAiApi} instances are created so that chat and embedding
 * providers can point at different base-URLs (e.g. OpenRouter for chat +
 * LMStudio for embeddings).
 * <p>
 * The entire configuration is inactive in the {@code stub} profile; stub
 * implementations are provided by {@link com.policyguard.config.stub.StubChatModel}
 * and {@link com.policyguard.config.stub.StubEmbeddingModel} instead.
 */
@Configuration
@Profile("!stub")
public class AiConfig {

    private final PolicyguardProperties properties;

    public AiConfig(PolicyguardProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "chatOpenAiApi")
    public OpenAiApi chatOpenAiApi() {
        PolicyguardProperties.Chat chat = properties.getChat();
        return OpenAiApi.builder()
                .baseUrl(chat.getBaseUrl())
                .apiKey(chat.getApiKey())
                .build();
    }

    @Bean(name = "embeddingOpenAiApi")
    public OpenAiApi embeddingOpenAiApi() {
        PolicyguardProperties.Embedding emb = properties.getEmbedding();
        return OpenAiApi.builder()
                .baseUrl(emb.getBaseUrl())
                .apiKey(emb.getApiKey())
                .build();
    }

    @Bean
    public OpenAiChatModel openAiChatModel(
            @Qualifier("chatOpenAiApi") OpenAiApi api) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getChat().getModel())
                .build();
        return new OpenAiChatModel(api, options);
    }

    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(
            @Qualifier("embeddingOpenAiApi") OpenAiApi api) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(properties.getEmbedding().getModel())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, options);
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        return ChatClient.create(chatModel);
    }
}
