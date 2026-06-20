package com.itsupport.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                    You are a helpful IT support triage assistant. 
                    You help classify, analyse, and draft responses to IT support tickets.
                    Always be professional, empathetic, and precise.
                    When drafting responses, be specific and actionable with numbered steps.
                    """)
                .build();
    }
}
