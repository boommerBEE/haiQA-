package com.elong.haizdemo01.controller;
//负责用户提问

import com.elong.haizdemo01.dto.ChatResponse;
import com.elong.haizdemo01.service.ChatService;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    // 构造器注入（@Lazy：未配置 AI / 向量库时仍可完成 Spring 上下文启动）
    public ChatController(@Lazy ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * RAG 问答接口
     */
    @PostMapping
    public ChatResponse chat(@RequestBody Map<String, String> request) {

        // 获取用户问题
        String question = request.get("question");

        // 调用 service；Spring 会自动用 Jackson 把 ChatResponse 序列化成 JSON
        return chatService.chat(question);
    }
}