# 使用Spring AI Alibaba实现的智能客服

> Spring AI Alibaba Repo: https://github.com/alibaba/spring-ai-alibaba
>
> Spring AI Alibaba Website: https://java2ai.com/docs/frameworks/studio/quick-start
>
> Spring AI Alibaba Website Repo: https://github.com/springaialibaba/spring-ai-alibaba-website

EcommSpringBot 是一个基于 SpringAI Alibaba 的企业级智能客服助手系统，支持用户通过自然语言交互完成订单查询、自动取消、规则问答等操作，融合微服务架构、RAG 技术、Redis 聊天上下文记忆与向量数据库，实现私有化可扩展的智能体服务平台。

## 项目亮点

- 基于 SpringAI Alibaba 实现企业智能体对话能力（支持通义千问/OpenAI）
- 模块化设计：订单服务、中台服务、SSE 方式连接 MCP 服务、RAG 知识库、记忆模块清晰分离
- 支持自然语言查询订单、自动取消订单
- 使用 Milvus 向量数据库构建 RAG 知识库，支持语义检索增强
- 使用 Redis + Milvus 实现多层对话记忆（短期 / 长期）

## 模块结构

```
EcommSpringBot/
├── mall-order/                       # 核心订单服务（Spring Boot + MySQL）
├── mall-order-cmp-server/            # CMP 中台服务（封装订单能力）
├── mall-order-cmp_sso-client/        # 提供 Controller 接口 + Redis 聊天上下文支持
├── mall-order-es-rag/                # ES 向量知识库模块（退换货规则文档 → ES 向量）
├── mall-order-graph-server/          # MCP 图计算服务（基于 Spring AI Alibaba）
├── mall-order-milvus-rag/            # Milvus RAG 向量检索服务（端口 8086）
├── mall-order-milvus-memory/         # 多层对话记忆模块（Redis 短期 + Milvus 长期）
└── README.md
```

## 功能特性

### 1. 自然语言操作订单服务
- 用户可使用自然语言对话，如：
  - "我想查询 用户USER1005 的订单"
  - "帮我取消用户USER1005 的订单"
- 通过 Spring AI Alibaba Function Calling 自动匹配接口并调用 mall-order 服务完成。

### 2. SSE 实时回复 + Redis 上下文记忆
- 使用 SSE（Server-Sent Events）建立流式连接
- 基于 Redis 实现用户对话上下文记忆
- 支持连续上下文查询、简洁自然的人机交互体验

### 3. 向量检索增强（RAG）
- 使用 text-embedding-v2 模型生成向量，存入 Milvus 向量数据库
- 支持 PDF 文档导入、文本分块、语义搜索
- 用户提问如"退货流程是什么？"可自动语义匹配文档内容并回答

### 4. 多层对话记忆
- 短期记忆（Redis）：当前对话上下文，自动过期淘汰
- 长期记忆（Milvus）：用户画像、事实、对话摘要持久化
- 规则+LLM 两阶段提取：从对话中自动提取值得长期记录的信息

---

## mall-order-milvus-rag — RAG 向量检索模块

基于 Spring AI + Milvus 的 RAG（检索增强生成）服务。支持 PDF 文档导入、文本分块、语义搜索。

### 技术架构

```
用户请求 → Text Embedding → Milvus 向量检索 → 返回相似文档块
               ↑
        DashScope text-embedding-v2 (1536维)
```

### 核心依赖

| 组件 | 说明 |
|------|------|
| `spring-ai-starter-vector-store-milvus` | Milvus 向量存储自动配置 |
| `milvus-sdk-java` 2.5.4 | Milvus 低层 SDK |
| `spring-ai-autoconfigure-model-openai` | OpenAI 兼容 embedding（对接 DashScope） |
| `spring-ai-pdf-document-reader` | PDF 文档解析（基于 PDFBox） |
| `spring-ai-rag` | Spring AI RAG 支持 |

### 配置

```yaml
spring:
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      embedding:
        options:
          model: text-embedding-v2
    vectorstore:
      milvus:
        host: localhost
        port: 19530
        collection-name: mall_rag_collection
        dimensions: 1536
        index-type: IVF_FLAT
        metric-type: COSINE
        initialize-schema: true
```

### REST API（端口 8086）

| 端点 | 方法 | 说明 |
|------|------|------|
| `/vector/milvus/health` | GET | 健康检查 |
| `/vector/milvus/documents` | POST | 添加单条文本到向量库 |
| `/vector/milvus/documents/batch` | POST | 批量添加文本 |
| `/vector/milvus/documents/pdf` | POST | 上传 PDF 导入向量库 |
| `/vector/milvus/search` | POST | 语义搜索（支持相似度阈值） |
| `/vector/milvus/search` | GET | 语义搜索（简易版） |
| `/vector/milvus/stats` | GET | 服务状态统计 |

### 文档分块策略

使用 `TokenTextSplitter(500, 100, 5, 10000, true)`：
- 每块最大 500 token
- 块间重叠 100 token（保持上下文连贯）
- PDF 先按页拆分，再按 token 分块

### 注意

- 使用 DashScope（阿里云）OpenAI 兼容接口，需设置 `DASHSCOPE_API_KEY` 环境变量
- text-embedding-v2 固定输出 1536 维向量，与 Milvus 集合维度一致

---

## mall-order-milvus-memory — 多层对话记忆模块

实现短期（Redis）+ 长期（Milvus）多层对话记忆系统，并支持规则+LLM 两阶段记忆提取。

### 技术架构

```
对话交互
    │
    ▼
┌─────────────────────────────────────────┐
│  HybridMemoryManager                    │
│  ┌──────────────────┐ ┌──────────────┐ │
│  │ ShortTermMemory  │ │ LongTermMem  │ │
│  │ (Redis)          │ │ (Milvus)     │ │
│  │ 对话上下文        │ │ 用户画像      │ │
│  │ 自动过期         │ │ 事实记忆      │ │
│  │                  │ │ 对话摘要      │ │
│  └──────┬───────────┘ └──────┬───────┘ │
│         │                    │         │
│         ▼                    │         │
│  ┌───────────────────┐       │         │
│  │ MemoryExtractor   │       │         │
│  │ 规则正则预过滤      │       │         │
│  │ → LLM 语义精提取   │───────┘         │
│  └───────────────────┘                  │
│         ▲                              │
│  ┌───────────────────┐                  │
│  │ ConsolidationSvc  │ 定时触发          │
│  └───────────────────┘                  │
└─────────────────────────────────────────┘
```

### 三层记忆结构

| 层级 | 存储 | 集合/介质 | 生命周期 | 说明 |
|------|------|-----------|----------|------|
| 短期 | Redis | `RedisChatMemory` | 1 小时 | 当前对话原始消息，自动淘汰最旧 |
| 长期-画像 | Milvus | `memory_user_profile` | 持久 | 用户偏好、角色、习惯等 |
| 长期-事实 | Milvus | `memory_fact` | 持久 | 业务规则、客观信息 |
| 长期-摘要 | Milvus | `memory_summary` | 持久 | 对话高层概括 |

### 记忆提取策略（两阶段）

**阶段一 — 规则预过滤**：正则表达式匹配常见模式
- 偏好表达：`我喜欢XX`、`我不想XX`、`叫我XX` 等
- 事实表达：`规则是XX`、`不支持XX`、`需要XX` 等
- 人物画像：`我是XX`、`我的职位XX` 等

**阶段二 — LLM 精提取**：将候选文本传给 LLM，输出结构化 JSON

```json
{
  "memories": [
    {"type": "USER_PROFILE", "content": "用户是一名电商运营人员"},
    {"type": "FACT", "content": "退货需要在签收后7天内申请"},
    {"type": "SUMMARY", "content": "用户询问了退货政策"}
  ]
}
```

### 自动合并机制

1. 短期记忆消息数达到 `consolidation-threshold`（默认 20）时标记可合并
2. 定时任务（默认每 5 分钟）读取短期记忆内容
3. `MemoryExtractor` 提取记忆 → `EmbeddingModel` 生成向量 → 存入 Milvus

### 配置

```yaml
memory:
  enabled: true
  user-id: default_user
  conversation-id: default_conversation
  consolidation-threshold: 20

  short-term:
    max-size: 20
    key-prefix: memory:short:
    ttl-seconds: 3600

  long-term:
    dimension: 1536
    milvus:
      host: localhost
      port: 19530

  extractor:
    enabled: true
    model: qwen-max

  consolidation:
    enabled: true
    interval-ms: 300000
```

### 使用方式

在其他模块中引入依赖即可自动装配：

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>mall-order-milvus-memory</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

注入 `HybridMemoryManager` 使用：

```java
@Autowired
private HybridMemoryManager hybridMemoryManager;

// 记录对话
hybridMemoryManager.addExchange("用户消息", "助手回复");

// 构建检索上下文
String context = hybridMemoryManager.buildContext(queryEmbedding);

// 手动触发合并
memoryConsolidationService.consolidate();
```

---

## 快速启动

### 前提条件

- JDK 17+
- Maven 3.9+
- Redis
- MySQL
- Milvus 向量数据库
- DashScope API Key（环境变量 `DASHSCOPE_API_KEY`）

### 启动顺序

```bash
# 启动 mall-order 服务
cd mall-order && mvn spring-boot:run

# 启动 mall-order-cmp-server
cd ../mall-order-cmp-server && mvn spring-boot:run

# 启动 sso-client 接口层（含AI智能体接口）
cd ../mall-order-cmp_sso-client && mvn spring-boot:run

# 启动 es-rag 服务（用于 ES 知识库管理）
cd ../mall-order-es-rag && mvn spring-boot:run

# 启动 milvus-rag 服务（Milvus 向量 RAG，端口 8086）
cd ../mall-order-milvus-rag && mvn spring-boot:run
```

> **milvus-memory 为库模块**，无需独立启动，引入依赖后随宿主应用自动装配。

### 共同依赖

| 依赖 | 用途 | RAG | Memory |
|------|------|-----|--------|
| DashScope text-embedding-v2 | 文本向量化 | 文档搜索 | 记忆存储 |
| Milvus 向量数据库 | 向量存储与检索 | 文档块 | 长期记忆 |
| Spring AI 1.1.0 | AI 抽象层 | vector store | embedding/chat |
| Java 17 + Spring Boot 3.5.7 | 运行环境 | 通用 | 通用 |

---

## 许可证

本项目基于 Apache 2.0 License 开源，可自由使用、修改与分发。

如果你觉得该项目有价值，欢迎 Star 或 Fork！

📬 联系作者：996766130@qq.com

## 前端页面

![img_1.png](img_1.png)
