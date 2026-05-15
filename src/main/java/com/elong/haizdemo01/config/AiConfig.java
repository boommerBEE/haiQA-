package com.elong.haizdemo01.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    // 1. DeepSeek 大模型配置 (复用 OpenAI 客户端)；@Lazy 避免 refresh 阶段预实例化（无密钥或未调用聊天前不创建客户端)
    @Bean
    @Lazy
    public ChatLanguageModel deepseekChatModel(
            @Value("${ai.deepseek.api-key}") String apiKey,
            @Value("${ai.deepseek.base-url}") String baseUrl,
            @Value("${ai.deepseek.model-name}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    // 2. Ollama 本地 Embedding 模型配置
    @Bean
    @Lazy
    public EmbeddingModel ollamaEmbeddingModel(
            @Value("${ai.ollama.base-url}") String baseUrl,
            @Value("${ai.ollama.model-name}") String modelName) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    // 3. Chroma 向量数据库配置（@Lazy：Chroma 未启动时不阻塞应用启动）
    //
    // 已知问题：langchain4j 0.36.2 的 ChromaEmbeddingStore 构造时无脑调用 createCollection，
    //          若集合已存在则直接抛 409 RuntimeException，导致 Spring Boot 重启失败。
    //          这里加一层"自愈"：捕获 409 后调用 Chroma DELETE API 删掉集合再重建。
    //          代价：重启后已有向量数据会丢失，需要重新上传文档（开发期可接受）。
    @Bean
    @Lazy
    public EmbeddingStore<TextSegment> chromaEmbeddingStore(
            @Value("${ai.chroma.base-url}") String baseUrl,
            @Value("${ai.chroma.collection-name}") String collectionName) {
        try {
            return buildChromaStore(baseUrl, collectionName);
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (!msg.contains("status code: 409")) {
                throw e; // 其他错误原样抛出
            }
            log.warn("Chroma 集合 '{}' 已存在（409），将先删除后重建。已有向量数据会丢失。", collectionName);
            deleteChromaCollection(baseUrl, collectionName);
            return buildChromaStore(baseUrl, collectionName);
        }
    }

    //增加记忆功能
    @Bean
    public ChatMemory chatMemory() {
        //窗口型记忆：不会保留所有消息，只保留最近 n 条消息（因为 LLM 上下文长度有限）
        return MessageWindowChatMemory.withMaxMessages(10);
    }

    private static EmbeddingStore<TextSegment> buildChromaStore(String baseUrl, String collectionName) {
        return ChromaEmbeddingStore.builder()
                .baseUrl(baseUrl)
                .collectionName(collectionName)
                .build();
    }

    private static void deleteChromaCollection(String baseUrl, String collectionName) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            // 注意：chromadb 0.6.3 的 v1 DELETE 端点存在 async bug（"cannot unpack non-iterable coroutine object"），
            // 因此走 v2 路径（带 tenant/database），与 langchain4j 默认创建时使用的命名空间一致。
            String deleteUrl = baseUrl.replaceAll("/+$", "")
                    + "/api/v2/tenants/default_tenant/databases/default_database/collections/"
                    + collectionName;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .timeout(Duration.ofSeconds(10))
                    .DELETE()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("已删除 Chroma 集合 '{}'", collectionName);
            } else {
                log.warn("删除 Chroma 集合 '{}' 返回状态 {}: {}", collectionName, resp.statusCode(), resp.body());
            }
        } catch (Exception ex) {
            log.error("删除 Chroma 集合 '{}' 失败", collectionName, ex);
            throw new RuntimeException("Failed to delete Chroma collection: " + collectionName, ex);
        }
    }
}