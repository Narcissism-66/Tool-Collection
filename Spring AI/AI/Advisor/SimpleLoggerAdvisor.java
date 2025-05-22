package com.example.backend.controller.AI.Advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.model.MessageAggregator;
import reactor.core.publisher.Flux;

/**
 * 简单日志记录顾问类
 * 
 * 这个类实现了StreamAroundAdvisor接口，用于记录AI处理过程中的请求和响应信息。
 * 它在AI处理流程的前后都进行干预，主要用于日志记录和调试目的。
 * 
 * Advisor是Spring AI中的一种拦截器机制，可以在不修改核心代码的情况下增强AI处理流程。
 */
public class SimpleLoggerAdvisor implements StreamAroundAdvisor {

    // 创建日志记录器
    private static final Logger logger = LoggerFactory.getLogger(SimpleLoggerAdvisor.class);

    /**
     * 获取当前Advisor的名称
     * 返回类的简单名称作为Advisor名称，用于在日志和调试中标识
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
     * 这里设置为0，意味着它将在所有其他Advisor之前执行
     * 
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 核心方法：在流处理过程中进行干预
     * 
     * 本方法在请求处理前记录请求信息，并在响应生成后记录响应信息
     * 
     * @param advisedRequest 包含用户请求的对象
     * @param chain 处理链，用于传递请求到下一个Advisor或模型
     * @return 原始的响应流（不修改内容）
     */
    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        // 记录用户请求信息
        logger.info("Message: {}", advisedRequest);

        // 将请求传递给链中的下一个处理器，获取响应流
        Flux<AdvisedResponse> advisedResponses = chain.nextAroundStream(advisedRequest);

        // 使用MessageAggregator聚合流中的消息，并记录每个响应
        // 这里不对响应内容进行修改，只是记录日志
        return new MessageAggregator().aggregateAdvisedResponse(advisedResponses,
                advisedResponse -> {
                    logger.info("AFTER: {}", advisedResponse);
                });
    }
}