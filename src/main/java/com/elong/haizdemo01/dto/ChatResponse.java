package com.elong.haizdemo01.dto;

import java.util.List;

/**
 * RAG 问答接口的返回体。
 * <p>
 * 与单纯返回 {@code String} 相比，这里把"回答"和"引用来源"拆成结构化字段，前端可以：
 * <ul>
 *   <li>把 {@code answer} 渲染成主文本</li>
 *   <li>把 {@code sources} 渲染成"参考资料"折叠卡片，让用户点开验证回答的可信度</li>
 * </ul>
 * 这就是大家说的"可追溯回答（traceable answer）"。
 */
public record ChatResponse(
        String answer,
        List<Source> sources
) {
    /**
     * 单条引用来源 = 一个被检索召回的文档切片。
     *
     * @param fileName 来自 {@code Metadata("file_name", ...)}，即上传时的原始文件名
     * @param content  切片正文（已截断到合理长度，避免响应体爆炸）
     * @param score    向量相似度得分（langchain4j 内部按余弦距离计算，越接近 1 越相关）
     */
    public record Source(
            String fileName,
            String content,
            double score
    ) {}
}
