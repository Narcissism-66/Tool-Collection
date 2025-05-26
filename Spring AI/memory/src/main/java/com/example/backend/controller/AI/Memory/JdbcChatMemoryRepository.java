package com.example.backend.controller.AI.Memory;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * JdbcChatMemoryRepository - 基于JDBC的聊天记忆存储实现
 * 
 * 该类实现了ChatMemoryRepository接口，提供了使用关系型数据库存储聊天消息的功能。
 * 主要功能包括：
 * 1. 存储和检索聊天消息
 * 2. 管理会话ID
 * 3. 支持事务操作
 * 4. 支持多种数据库方言
 * 
 * 使用方式：
 * 1. 通过Builder模式创建实例
 * 2. 必须提供数据源或JdbcTemplate
 * 3. 可以自定义数据库方言
 * 
 * 示例：
 * JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
 *     .dataSource(dataSource)
 *     .build();
 */
public final class JdbcChatMemoryRepository implements ChatMemoryRepository {

    /** JdbcTemplate实例，用于执行SQL操作 */
    private final JdbcTemplate jdbcTemplate;

    /** 事务模板，用于管理数据库事务 */
    private final TransactionTemplate transactionTemplate;

    /** 数据库方言，用于处理不同数据库的SQL语法差异 */
    private final JdbcChatMemoryRepositoryDialect dialect;

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(JdbcChatMemoryRepository.class);

    /**
     * 私有构造函数，通过Builder模式创建实例
     * 
     * @param jdbcTemplate JDBC操作模板
     * @param dialect 数据库方言
     * @param txManager 事务管理器，如果为null则使用默认的DataSourceTransactionManager
     * @throws IllegalArgumentException 如果jdbcTemplate或dialect为null
     */
    private JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, JdbcChatMemoryRepositoryDialect dialect,
                                     PlatformTransactionManager txManager) {
        Assert.notNull(jdbcTemplate, "jdbcTemplate cannot be null");
        Assert.notNull(dialect, "dialect cannot be null");
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
        this.transactionTemplate = new TransactionTemplate(
                txManager != null ? txManager : new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    /**
     * 查找所有会话ID
     * 
     * @return 所有会话ID的列表
     */
    @Override
    public List<String> findConversationIds() {
        return this.jdbcTemplate.queryForList(this.dialect.getSelectConversationIdsSql(), String.class);
    }

    /**
     * 根据会话ID查找所有消息
     * 
     * @param conversationId 会话ID
     * @return 该会话的所有消息列表
     * @throws IllegalArgumentException 如果conversationId为空
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        return this.jdbcTemplate.query(this.dialect.getSelectMessagesSql(), new MessageRowMapper(), conversationId);
    }

    /**
     * 保存会话的所有消息
     * 注意：此操作会先删除该会话的现有消息，然后保存新消息
     * 
     * @param conversationId 会话ID
     * @param messages 要保存的消息列表
     * @throws IllegalArgumentException 如果conversationId为空或messages为null
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        Assert.noNullElements(messages, "messages cannot contain null elements");

        this.transactionTemplate.execute(status -> {
            deleteByConversationId(conversationId);
            this.jdbcTemplate.batchUpdate(this.dialect.getInsertMessageSql(),
                    new AddBatchPreparedStatement(conversationId, messages));
            return null;
        });
    }

    /**
     * 删除指定会话的所有消息
     * 
     * @param conversationId 要删除的会话ID
     * @throws IllegalArgumentException 如果conversationId为空
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        this.jdbcTemplate.update(this.dialect.getDeleteMessagesSql(), conversationId);
    }

    /**
     * 创建Builder实例
     * 
     * @return 新的Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 批量插入消息的预处理语句设置器
     * 用于优化批量插入消息的性能
     */
    private record AddBatchPreparedStatement(String conversationId, List<Message> messages,
                                             AtomicLong instantSeq) implements BatchPreparedStatementSetter {

        private AddBatchPreparedStatement(String conversationId, List<Message> messages) {
            this(conversationId, messages, new AtomicLong(Instant.now().toEpochMilli()));
        }

        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            var message = this.messages.get(i);

            ps.setString(1, this.conversationId);
            ps.setString(2, message.getText());
            ps.setString(3, message.getMessageType().name());
            ps.setTimestamp(4, new Timestamp(this.instantSeq.getAndIncrement()));
        }

        @Override
        public int getBatchSize() {
            return this.messages.size();
        }
    }

    /**
     * 消息行映射器
     * 负责将数据库查询结果映射为Message对象
     */
    private static class MessageRowMapper implements RowMapper<Message> {

        @Override
        @Nullable
        public Message mapRow(ResultSet rs, int i) throws SQLException {
            var content = rs.getString(1);
            var type = MessageType.valueOf(rs.getString(2));

            return switch (type) {
                case USER -> new UserMessage(content);
                case ASSISTANT -> new AssistantMessage(content);
                case SYSTEM -> new SystemMessage(content);
                // The content is always stored empty for ToolResponseMessages.
                // If we want to capture the actual content, we need to extend
                // AddBatchPreparedStatement to support it.
                case TOOL -> new ToolResponseMessage(List.of());
            };
        }

    }

    /**
     * Builder类 - 用于构建JdbcChatMemoryRepository实例
     * 
     * 使用方式：
     * JdbcChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
     *     .dataSource(dataSource)
     *     .dialect(dialect)
     *     .transactionManager(txManager)
     *     .build();
     */
    public static final class Builder {
        /** JDBC模板 */
        private JdbcTemplate jdbcTemplate;

        /** 数据库方言 */
        private JdbcChatMemoryRepositoryDialect dialect;

        /** 数据源 */
        private DataSource dataSource;

        /** 事务管理器 */
        private PlatformTransactionManager platformTransactionManager;

        /** Builder的日志记录器 */
        private static final Logger logger = LoggerFactory.getLogger(Builder.class);

        private Builder() {
        }

        /**
         * 设置JdbcTemplate
         * 
         * @param jdbcTemplate JDBC操作模板
         * @return Builder实例，用于链式调用
         */
        public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
            return this;
        }

        /**
         * 设置数据库方言
         * 
         * @param dialect 数据库方言实现
         * @return Builder实例，用于链式调用
         */
        public Builder dialect(JdbcChatMemoryRepositoryDialect dialect) {
            this.dialect = dialect;
            return this;
        }

        /**
         * 设置数据源
         * 
         * @param dataSource 数据源
         * @return Builder实例，用于链式调用
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        /**
         * 设置事务管理器
         * 
         * @param txManager 事务管理器
         * @return Builder实例，用于链式调用
         */
        public Builder transactionManager(PlatformTransactionManager txManager) {
            this.platformTransactionManager = txManager;
            return this;
        }

        /**
         * 构建JdbcChatMemoryRepository实例
         * 
         * @return 新的JdbcChatMemoryRepository实例
         * @throws IllegalArgumentException 如果必要参数未设置
         * @throws IllegalStateException 如果无法检测数据库方言
         */
        public JdbcChatMemoryRepository build() {
            DataSource effectiveDataSource = resolveDataSource();
            JdbcChatMemoryRepositoryDialect effectiveDialect = resolveDialect(effectiveDataSource);
            return new JdbcChatMemoryRepository(resolveJdbcTemplate(), effectiveDialect,
                    this.platformTransactionManager);
        }

        private JdbcTemplate resolveJdbcTemplate() {
            if (this.jdbcTemplate != null) {
                return this.jdbcTemplate;
            }
            if (this.dataSource != null) {
                return new JdbcTemplate(this.dataSource);
            }
            throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
        }

        private DataSource resolveDataSource() {
            if (this.dataSource != null) {
                return this.dataSource;
            }
            if (this.jdbcTemplate != null && this.jdbcTemplate.getDataSource() != null) {
                return this.jdbcTemplate.getDataSource();
            }
            throw new IllegalArgumentException("DataSource must be set (either via dataSource() or jdbcTemplate())");
        }

        private JdbcChatMemoryRepositoryDialect resolveDialect(DataSource dataSource) {
            if (this.dialect == null) {
                try {
                    return JdbcChatMemoryRepositoryDialect.from(dataSource);
                }
                catch (Exception ex) {
                    throw new IllegalStateException("Could not detect dialect from datasource", ex);
                }
            }
            else {
                warnIfDialectMismatch(dataSource, this.dialect);
                return this.dialect;
            }
        }

        /**
         * Logs a warning if the explicitly set dialect differs from the dialect detected
         * from the DataSource.
         */
        private void warnIfDialectMismatch(DataSource dataSource, JdbcChatMemoryRepositoryDialect explicitDialect) {
            try {
                JdbcChatMemoryRepositoryDialect detected = JdbcChatMemoryRepositoryDialect.from(dataSource);
                if (!detected.getClass().equals(explicitDialect.getClass())) {
                    logger.warn("Explicitly set dialect {} will be used instead of detected dialect {} from datasource",
                            explicitDialect.getClass().getSimpleName(), detected.getClass().getSimpleName());
                }
            }
            catch (Exception ex) {
                logger.debug("Could not detect dialect from datasource", ex);
            }
        }

    }

}