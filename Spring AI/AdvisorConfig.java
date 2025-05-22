package com.example.backend.config;

import com.example.backend.controller.AI.Advisor.ContentFilterAdvisor;
import com.example.backend.controller.AI.Advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Advisor配置类
 * 
 * 这个类负责配置和注册所有的AI处理顾问(Advisor)，并将它们注入到ChatClient中。
 * 在Spring AI中，Advisor是一种AOP机制，允许在AI处理流程的不同阶段进行干预。
 * 
 * 主要包含：
 * 1. 日志记录顾问 - 记录请求和响应信息
 * 2. 内容过滤顾问 - 检测用户输入是否包含敏感内容
 */
@Configuration
public class AdvisorConfig {

    /**
     * 注册SimpleLoggerAdvisor
     * 
     * 该Advisor用于记录AI处理前后的日志信息，主要用于调试和监控。
     * Order值为0，最先执行。
     * 
     * @return SimpleLoggerAdvisor实例
     */
    @Bean
    public SimpleLoggerAdvisor loggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }
    
    /**
     * 注册ContentFilterAdvisor
     *
     * 该Advisor用于过滤用户输入，检查是否包含敏感词，
     * 如包含则拦截请求并返回自定义警告。
     * Order值为5，在日志记录之后执行。
     * 
     * @return ContentFilterAdvisor实例
     */
    @Bean
    public ContentFilterAdvisor contentFilterAdvisor(){
        return new ContentFilterAdvisor();
    }

    /**
     * 构造ChatClient并注入所有Advisor
     *
     * ChatClient是与AI模型交互的主要客户端接口。
     * 这里将所有配置的Advisor注入到ChatClient中，
     * 使它们在AI处理流程的不同阶段生效。
     *
     * 执行顺序：
     * 1. SimpleLoggerAdvisor (Order=0)
     * 2. ContentFilterAdvisor (Order=5)
     * 3. [AI模型处理请求]
     * 
     * @param loggerAdvisor 日志记录顾问
     * @param contentFilterAdvisor 内容过滤顾问
     * @param builder ChatClient构建器，由Spring Boot自动配置注入
     * @return 配置好的ChatClient实例
     */
    @Bean
    public ChatClient chatClient(SimpleLoggerAdvisor loggerAdvisor,
                                 ContentFilterAdvisor contentFilterAdvisor,
                                 ChatClient.Builder builder) {// 注入 Spring Boot 自动配置的 Builder
        return builder
                .defaultAdvisors(loggerAdvisor, contentFilterAdvisor)  // 或 builder.defaultAdvisors(spec -> spec.add(loggerAdvisor))
                .build();
    }
}
