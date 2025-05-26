package com.example.backend.controller.AI.Memory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class ChatMemoryConfig {
    
    @Bean
    @Primary
    public JdbcChatMemoryRepository chatMemoryRepository(DataSource dataSource) {
        return JdbcChatMemoryRepository.builder()
                .dataSource(dataSource)
                .dialect(new MysqlChatMemoryRepositoryDialect())
                .build();
    }
} 