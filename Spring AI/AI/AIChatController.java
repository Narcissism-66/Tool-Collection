package com.example.backend.controller.AI;


import com.example.backend.controller.AI.Advisor.SimpleLoggerAdvisor;
import com.example.backend.entity.AIChat;
import com.example.backend.entity.RestBean;
import com.example.backend.service.AIService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/AI/")
public class AIChatController {

    @Resource
    AIService aiService;

    private final ChatClient chatClient;

    public AIChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    //获取之前的对话记录
    @GetMapping("/getAIChatByUserId")
    public RestBean<List<AIChat>> getAIChatByUserId(@RequestParam("userId") Integer userId) {
        return RestBean.success("cg",aiService.getAIChatByUserId(userId));
    }

    //-------------------------------基础对话----------------------------------------

    //最简单的问答
    @GetMapping("/chat1")
    public String chat(@RequestParam("message") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    //流式输出---在跨域里面需要配置异步操作（实现configureAsyncSupport）、JWT也需要配置asyncSupported = true
    @GetMapping(value = "/chat2", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
    public Flux<String> chat2(@RequestParam("message") String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content()
                .delayElements(Duration.ofMillis(100));//延迟
    }

    //-------------------------------基础对话----------------------------------------


    //-------------------------------记忆对话----------------------------------------
    @Autowired
    ChatMemory chatMemory;
    //流式+窗口记忆化
    @GetMapping(value = "/chat3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat3(@RequestParam("message") String message) {
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(10)//设置窗口的大小
                .build();

        Message userMessage = new UserMessage(message);//因为存储的消息需要是Message的而不是字符串，需要转换一下
        memory.add("test", userMessage);

        StringBuilder aiReplyBuilder = new StringBuilder();//字符串拼接

        Flux<String> aiResponseFlux = chatClient.prompt()
                .messages(memory.get("test"))//根据记忆进行回复
                .stream()
                .content()
                .doOnNext(aiReplyBuilder::append) // 每收到一段就拼接
                .doOnComplete(() -> {
                    // 流式结束后，把完整回复存入memory
                    String fullReply = aiReplyBuilder.toString();
                    Message aiMessage = new AssistantMessage(fullReply);
                    memory.add("test", aiMessage);//记忆化存储
                });
        return aiResponseFlux.delayElements(Duration.ofMillis(100));
    }

    //数据库存储+流式输出+永久记忆-----------------------没实现永久记忆---------------------------
    @GetMapping(value="/chat4", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat4(@RequestParam("message") String message,
                            @RequestParam("userId") Integer userId) {


        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(20)//设置窗口的大小
                .build();


        Message userMessage = new UserMessage(message);
        chatMemory.add(userId.toString(), userMessage);

        StringBuilder aiReplyBuilder = new StringBuilder();

        // 获取历史消息并生成回复
        Flux<String> aiResponseFlux = chatClient.prompt()
                .messages(chatMemory.get(userId.toString()))
                .stream()
                .content()
                .doOnNext(aiReplyBuilder::append)
                .doOnComplete(() -> {
                    // 流式结束后，保存完整对话到数据库
                    String fullReply = aiReplyBuilder.toString();
                    Message aiMessage = new AssistantMessage(fullReply);
                    chatMemory.add(userId.toString(), aiMessage);
                    aiService.AddAiChat(new AIChat(userId, message, fullReply,new Date()));
                });
        return aiResponseFlux.delayElements(Duration.ofMillis(100));
    }

    //-------------------------------记忆对话----------------------------------------


    // -----------------------------Advisors------------------------------------------
    /**
     * 在AdvisorConfig里面已经注册好了
     * 需要使用我们自己的chatClient
     * @Autowired
     * private ChatClient chatClientOfAdvisor;//为了区分是不是自己的ChatClient
     */
    @Autowired
    private ChatClient chatClientOfAdvisor;//为了区分是不是自己的ChatClient
    @GetMapping(value = "/chat5", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat5(@RequestParam("message") String message)
    {
        return chatClientOfAdvisor.prompt()
                .user(message)
                .stream()
                .content()
                .delayElements(Duration.ofMillis(100));
    }

    // -----------------------------Advisors------------------------------------------


    //---------------------------------Prompts----------------------------------------
    /**
     * 使用自定义提示模板的聊天API
     * Prompts类似于提示词工程，用于控制AI的行为、角色和回答风格
     * @param message 用户输入的消息
     * @return 流式输出的AI回复
     */
    @GetMapping(value = "/chat6", produces = MediaType.TEXT_EVENT_STREAM_VALUE )
    public Flux<String> chat6(@RequestParam("message") String message) {
        // 将用户消息转换为UserMessage对象
        Message userMessage = new UserMessage(message);
        
        // 定义系统提示模板文本，使用{name}和{voice}作为可替换变量
        // 这里定义了AI助手的角色、名称和回复风格
        String systemText = """
                          You are a helpful AI assistant that helps people find information.
                          Your name is {name}
                          You should reply to the user's request with your name and also in the style of a {voice}.
                          """;
        
        // 创建系统提示模板对象
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemText);
        
        // 填充模板变量，创建系统消息
        // 这里设置AI名称为"嘿嘿"，回复风格为"voice"
        Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "嘿嘿", "voice", "voice"));
        
        // 将用户消息和系统消息组合成一个Prompt对象
        // 系统消息会指导AI如何回复用户消息
        Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
        
        // 使用chatClient调用AI，传入自定义的prompt
        return chatClient.prompt(prompt)
                .stream()
                .content()
                .delayElements(Duration.ofMillis(100));//延迟100毫秒，使输出更平滑
    }
    //---------------------------------Prompts----------------------------------------
}
