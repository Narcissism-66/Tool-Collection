package com.example.backend.controller.AI.Memory;

/*
 * @Auther:fz
 * @Date:2025/5/26
 * @Description:
 */

import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;

public class MysqlChatMemoryRepositoryDialect implements JdbcChatMemoryRepositoryDialect {

    @Override
    public String getSelectMessagesSql() {
        return "SELECT content, type FROM spring_ai_chat_memory WHERE conversation_id = ? ORDER BY `timestamp`";
    }

    @Override
    public String getInsertMessageSql() {
        return "INSERT INTO spring_ai_chat_memory (conversation_id, content, type, `timestamp`) VALUES (?, ?, ?, ?)";
    }

    @Override
    public String getSelectConversationIdsSql() {
        return "SELECT DISTINCT conversation_id FROM spring_ai_chat_memory";
    }

    @Override
    public String getDeleteMessagesSql() {
        return "DELETE FROM spring_ai_chat_memory WHERE conversation_id = ?";
    }

}
