package com.apm.observatory.aipipeline.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring AI {@link ChatClient} 빈 설정. Ollama 채팅 모델을 감싼다. */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

}