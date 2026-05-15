package com.elong.haizdemo01.service;

import com.elong.haizdemo01.dto.ChatResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatMemory chatMemory;
    private final ChatLanguageModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    public ChatService(@Lazy ChatLanguageModel chatModel,
                       @Lazy EmbeddingModel embeddingModel,
                       @Lazy EmbeddingStore<TextSegment> embeddingStore,
                       ChatMemory chatMemory) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatMemory = chatMemory;
    }

    /**
     * RAG 问答：任一步失败会抛出 {@link ResponseStatusException}，由 {@link com.elong.haizdemo01.exception.ApiExceptionHandler} 转为可读 JSON。
     */
    public ChatResponse chat(String question) {

        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体需包含非空字段 question");
        }

        // 空值校验通过后再写入记忆，避免脏数据污染上下文
        chatMemory.add(UserMessage.from(question));

        Embedding questionEmbedding;
        try {
            questionEmbedding = embeddingModel.embed(question).content();
        } catch (Exception e) {
            log.error("Ollama 向量化失败", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Ollama 向量化失败：请确认本机已启动 Ollama（默认 http://localhost:11434），并已拉取 embedding 模型（默认 nomic-embed-text）。原因: "
                            + rootMessage(e),
                    e);
        }

        EmbeddingSearchResult<TextSegment> searchResult;
        try {
            searchResult = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(questionEmbedding)
                            .maxResults(3)
                            .build()
            );
        } catch (Exception e) {
            log.error("Chroma 向量检索失败", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Chroma 检索失败：请确认 Chroma 已启动（默认 http://localhost:8000），且集合 haipart_knowledge 已存在（可先调用文档上传接口写入）。原因: "
                            + rootMessage(e),
                    e);
        }

        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n"));

        String memoryContext = chatMemory.messages().toString();

        String prompt = """
                        你是一个工业零部件助手。
                        
                        以下是历史聊天记录：
                        %s
                        
                        请严格基于知识库内容回答问题。
                        如果知识库没有相关信息，请明确回答“不知道”。
                        
                        知识库内容：
                        %s
                        
                        用户问题：
                        %s
                        """.formatted(memoryContext, context, question);

        String answer;
        try {
            answer = chatModel.generate(prompt);
        } catch (Exception e) {
            log.error("大模型调用失败", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "大模型调用失败：请检查环境变量 DEEPSEEK_API_KEY 是否已配置、网络是否可访问 DeepSeek API。原因: " + rootMessage(e),
                    e);
        }

        // 把 AI 回答写入记忆，下一轮 chat 就能基于这一轮上下文回答
        chatMemory.add(AiMessage.from(answer));

        // 把检索到的 chunk 包装成 Source 列表一起返回，前端可展示"参考资料"
        List<ChatResponse.Source> sources = matches.stream()
                .map(ChatService::toSource)
                .toList();

        return new ChatResponse(answer, sources);
    }

    /** 把 langchain4j 的 EmbeddingMatch 转成对外暴露的 Source DTO。 */
    private static ChatResponse.Source toSource(EmbeddingMatch<TextSegment> match) {
        TextSegment seg = match.embedded();
        // 文件名来自 DocumentService 上传时塞入的 metadata("file_name", ...)
        String fileName = seg.metadata() != null ? seg.metadata().getString("file_name") : null;
        // 切片正文截断到 200 字符，避免响应体过大
        String content = truncate(seg.text(), 200);
        return new ChatResponse.Source(
                fileName != null ? fileName : "unknown",
                content,
                match.score()
        );
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String rootMessage(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getMessage();
        return m != null && !m.isBlank() ? m : c.getClass().getSimpleName();
    }

}
