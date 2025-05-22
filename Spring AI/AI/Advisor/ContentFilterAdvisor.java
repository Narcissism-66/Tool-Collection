package com.example.backend.controller.AI.Advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内容过滤顾问类
 * 
 * 这个类实现了StreamAroundAdvisor接口，用于在AI处理前过滤用户输入的内容，
 * 检测是否包含敏感词汇，如果包含则拦截请求并返回自定义响应。
 * 
 * 这是一个安全控制层，防止用户输入不适当的内容或尝试让AI生成有害回答。
 */
public class ContentFilterAdvisor implements StreamAroundAdvisor {
    // 创建日志记录器
    private static final Logger logger = LoggerFactory.getLogger(ContentFilterAdvisor.class);
    
    // 定义敏感词列表
    private final Set<String> sensitiveWords = new HashSet<>(Arrays.asList(
            "暴力", "色情", "赌博", "毒品", "政治", "恐怖", 
            "骚扰", "歧视", "侮辱", "威胁", "仇恨", "谁侮辱"
    ));
    
    /**
     * 获取当前Advisor的名称
     * 
     * @return Advisor的名称
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 设置Advisor的执行顺序
     * 数值越小，优先级越高
     * 这里设置为5，表示在SimpleLoggerAdvisor(0)之后执行，
     * 但在其他可能的Advisor之前执行
     * 
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return 5; // 在Logger之后执行
    }
    
    /**
     * 核心方法：在流处理过程中进行干预
     * 
     * 本方法在请求发送到AI模型前检查用户输入是否包含敏感内容
     * 如果包含敏感内容，则拦截请求并返回自定义警告响应
     * 如果不包含敏感内容，则允许请求继续到下一个处理器
     * 
     * @param advisedRequest 包含用户请求的对象
     * @param chain 处理链，用于传递请求到下一个Advisor或模型
     * @return 处理后的响应流，可能是自定义的警告响应或原始响应
     */
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        // 预处理请求，提取用户消息
        String userMessage = advisedRequest.userText();

        // 检查敏感内容
        if (containsSensitiveWords(userMessage)) {
            logger.warn("敏感内容被过滤: {}", userMessage);
            
            // 返回自定义响应，不继续调用链
            String responseText = "很抱歉，您的请求包含敏感内容，无法提供相关回答。请调整您的提问，避免包含不适当的内容。";
            
            // 创建响应消息和对象
            // 表示AI助手的回复消息
            AssistantMessage assistantMessage = new AssistantMessage(responseText);
            
            // 创建Generation对象 - Generation表示一次生成的内容
            Generation generation = new Generation(assistantMessage);
            List<Generation> generations = Collections.singletonList(generation);
            
            // 创建ChatResponse对象 - 包含所有生成的内容
            ChatResponse chatResponse = new ChatResponse(generations);
            
            // 创建AdvisedResponse对象 - 最终返回给用户的响应
            Map<String, Object> metadata = Collections.emptyMap();
            AdvisedResponse response = new AdvisedResponse(chatResponse, metadata);
            
            // 返回单个响应的Flux流
            return Flux.just(response);
        }
        
        // 如果没有敏感内容，继续处理链
        logger.info("内容审核通过: {}", userMessage);
        return chain.nextAroundStream(advisedRequest);
    }
    
    /**
     * 检查文本是否包含敏感词
     * 
     * @param text 要检查的文本
     * @return 如果包含敏感词返回true，否则返回false
     */
    private boolean containsSensitiveWords(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // 转换为小写进行比较
        String lowerText = text.toLowerCase();
        
        // 逐个检查敏感词
        for (String sensitiveWord : sensitiveWords) {
            if (lowerText.contains(sensitiveWord)) {
                logger.info("发现敏感词: [{}]", sensitiveWord);
                return true;
            }
        }
        
        // 如果没有找到敏感词
        logger.info("未发现敏感词");
        return false;
    }
} 