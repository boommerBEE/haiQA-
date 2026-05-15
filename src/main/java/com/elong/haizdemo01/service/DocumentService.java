package com.elong.haizdemo01.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class DocumentService {

    // 向量模型（Ollama）
    private final EmbeddingModel embeddingModel;

    // 向量数据库（Chroma）
    private final EmbeddingStore<TextSegment> embeddingStore;

    public DocumentService(@Lazy EmbeddingModel embeddingModel,
                           @Lazy EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 上传并写入知识库
     */
    public String upload(MultipartFile file) throws IOException {

        // 1. 读取 txt 文件内容
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);

        // 2. 构建 LangChain4j 文档对象
        Document document = Document.from(text, Metadata.from("file_name", file.getOriginalFilename()));

        // 3. 创建文档摄取器
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()

                // 指定 embedding 模型
                .embeddingModel(embeddingModel)

                // 指定向量数据库
                .embeddingStore(embeddingStore)

                // 文档切片策略
                .documentSplitter(
                        DocumentSplitters.recursive(
                                500, // chunkSize
                                100  // overlap
                        )
                )
                .build();

        // 4. 执行摄取（切片 + embedding + 存入 Chroma）
        ingestor.ingest(document);

        return "文档上传并向量化成功！";
    }
}