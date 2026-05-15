# haiz-demo01 — RAG 知识库问答 Demo

基于 Spring Boot 4 + LangChain4j + Ollama + ChromaDB + DeepSeek 的最小可运行 RAG 系统。

## 架构

```
浏览器 / test.http
      │
      ▼
ChatController / UploadController  (Spring MVC)
      │
      ▼
ChatService / DocumentService
      │
      ├─ 向量化 → Ollama (nomic-embed-text)
      ├─ 检索/写入 → ChromaDB (haipart_knowledge)
      └─ 生成回答 → DeepSeek (deepseek-chat, 走 OpenAI 兼容协议)
```

## 环境依赖（必须）

| 组件 | 版本 | 说明 |
|------|------|------|
| **JDK** | 21+ | Spring Boot 4.x 需要 Java 17+；项目实测用的是 JDK 21 |
| **Maven** | 通过 `mvnw` 自带 3.9.15 | 不需要额外安装 |
| **Python** | 3.10+ | 用于跑 ChromaDB |
| **chromadb** | **0.6.3**（重要） | 1.x 移除了 v1 API，langchain4j 0.36.2 仍用 v1，所以必须用 0.6.x |
| **Ollama** | 任意稳定版 | 用于本地 embedding |
| **Ollama 模型** | `nomic-embed-text` | `ollama pull nomic-embed-text` |
| **DeepSeek API Key** | — | https://platform.deepseek.com 注册获取，需有余额 |

> **关于 JDK 版本**：你的系统 `JAVA_HOME` 默认指向 JDK 11，会导致 `spring-boot-maven-plugin` 加载失败（`TypeNotPresentException`）。
> 解决方案：使用本仓库提供的 [`start-dev.cmd`](./start-dev.cmd)，它会强制使用 JDK 21；或在 IDEA Run Configuration 里指定 JDK 21。

## 启动流程

### 1) 启动 Ollama
```bash
ollama serve
ollama pull nomic-embed-text   # 仅首次
```

### 2) 启动 ChromaDB
```powershell
# 安装（仅首次）
pip install "chromadb==0.6.3"

# 启动（每次开发前）
& "$env:APPDATA\Python\Python314\Scripts\chroma.exe" run --host localhost --port 8000 --path ./chroma_data
```

### 3) 配置 DeepSeek API Key

**推荐**（系统环境变量，配置一次永久生效）：
```powershell
# 管理员 PowerShell
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxxxxxxx", "User")
# 重启 IDE 后生效
```

**或者** 在 IDEA Run Configuration → Environment Variables 里添加：
```
DEEPSEEK_API_KEY=sk-xxxxxxxx
```

### 4) 启动 Spring Boot

**命令行**：
```cmd
.\start-dev.cmd
```

**IDEA**：直接运行 `HaizDemo01Application` 即可（确保 Project SDK 是 JDK 21）。

## 接口测试

打开 [`test.http`](./test.http) 在 IntelliJ IDEA 里直接发请求，或：

```bash
# 上传文档
curl -X POST http://localhost:8080/document/upload -F "file=@test.txt"

# RAG 问答
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"哪个零件适合高温环境？"}'
```

## 已知坑（已踩过的雷）

| # | 现象 | 根因 | 解决 |
|---|------|------|------|
| 1 | `mvnw` 启动时 `TypeNotPresentException: RunMojo` | `JAVA_HOME` 指向 JDK 11，Spring Boot 4 的 plugin 类是 Java 17+ 字节码 | 用 `start-dev.cmd` 或 IDEA 指定 JDK 21 |
| 2 | `/chat` 返回 405 Method Not Allowed | langchain4j 0.30.0 用 Chroma v1 API，chromadb 1.x 已移除 v1 | 升 langchain4j 到 0.36.2 + 降 chromadb 到 0.6.x |
| 3 | Spring Boot 重启时 409 `Collection already exists` | langchain4j 0.36.2 的 `ChromaEmbeddingStore` 构造时无脑 `createCollection`，集合已存在直接抛异常 | 已在 `AiConfig` 加自愈：捕获 409 → DELETE 集合 → 重建（**代价：重启会丢已上传向量，需重新上传文档**） |
| 4 | `/chat` 返回 `Insufficient Balance` | DeepSeek 账户余额不足 | 充值或换 key |
| 5 | `/chat` 返回 `openAiApiKey cannot be null or empty` | 没设 `DEEPSEEK_API_KEY` 环境变量 | 见上面"3) 配置 DeepSeek API Key" |

## 目录结构

```
haiz-demo01/
├── src/main/java/com/elong/haizdemo01/
│   ├── config/AiConfig.java               # ChatModel / EmbeddingModel / EmbeddingStore 三个 Bean
│   ├── controller/
│   │   ├── ChatController.java            # POST /chat
│   │   └── UploadController.java          # POST /document/upload
│   ├── service/
│   │   ├── ChatService.java               # RAG 主流程
│   │   └── DocumentService.java           # 文档切片+向量化+入库
│   └── exception/ApiExceptionHandler.java # 统一异常 → JSON
├── src/main/resources/application.yml     # 配置（key 走环境变量）
├── test.txt                               # 测试用知识库文档
├── test.http                              # IDEA HTTP 客户端测试用例
├── start-dev.cmd                          # 一键启动脚本
└── pom.xml                                # Maven 依赖（langchain4j 0.36.2 全家桶）
```

## 后续 TODO

- [ ] `ChatService.chat()` 返回 `ChatResponse(answer, sources)` 结构化数据，便于前端展示引用来源
- [ ] 加多轮对话支持（`MessageWindowChatMemory` per session）
- [ ] 支持 PDF 上传（`langchain4j-document-parser-apache-pdfbox` 已在 pom）
- [ ] Web UI（React/Vue 都行）+ SSE 流式响应
