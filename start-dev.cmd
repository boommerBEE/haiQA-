@echo off
REM ============================================================
REM haiz-demo01 一键启动脚本
REM
REM 启动前请确保：
REM   1) Ollama 已运行（默认 http://localhost:11434），且已 pull nomic-embed-text
REM        > ollama serve
REM        > ollama pull nomic-embed-text
REM   2) ChromaDB 已运行（默认 http://localhost:8000）
REM        > "%APPDATA%\Python\Python314\Scripts\chroma.exe" run --host localhost --port 8000 --path ./chroma_data
REM
REM 本脚本作用：
REM   - 强制使用 JDK 21（系统 JAVA_HOME 默认指向 JDK 11，会导致 spring-boot-maven-plugin 加载失败）
REM   - 注入 DEEPSEEK_API_KEY 环境变量（避免硬编码到 application.yml）
REM   - 启动 Spring Boot 应用
REM ============================================================

setlocal

REM ---- 1. JDK 21 路径（按需修改）----
set "JAVA_HOME=D:\Environment\Java\JDK21"
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] JDK 21 not found at %JAVA_HOME%
    echo Please edit start-dev.cmd and set JAVA_HOME to your JDK 21 install path.
    exit /b 1
)

REM ---- 2. DeepSeek API Key（如未在系统环境变量里设置，则在此处填）----
if "%DEEPSEEK_API_KEY%"=="" (
    REM 推荐方式：在系统环境变量里设 DEEPSEEK_API_KEY，本脚本将自动透传
    REM 临时方式：取消下一行注释并填入 key（不要提交到版本库）
    REM set "DEEPSEEK_API_KEY=sk-xxxxxxxx"
    echo [WARN] DEEPSEEK_API_KEY is empty. /chat will fail. Set it in system env or edit this script.
)

REM ---- 3. 启动 ----
echo [INFO] JAVA_HOME = %JAVA_HOME%
echo [INFO] Starting Spring Boot...
call "%~dp0mvnw.cmd" spring-boot:run

endlocal
