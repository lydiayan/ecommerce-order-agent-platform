# Java AI Agent 订单平台项目附件版

我独立设计并完成了一个面向电商订单场景的 Java AI Agent 平台，覆盖订单查询、RAG 问答、售后操作、人工确认、Trace 观测和 bad case 闭环。

## 项目亮点

- 不是单纯聊天机器人，而是一条可解释、可回归、可接管的订单 Agent 执行链。
- 意图识别采用“规则优先 + 轻量模型兜底 + 不确定则澄清”的分层策略。
- 敏感操作必须进入人工确认，避免模型直接越权执行。
- Trace 会同步到 Elasticsearch，并可在 AgentInsight 中按 traceId 回看。
- 用户点踩后会沉淀为 bad case，进入持续优化闭环。

## RAG 设计

RAG 做成了完整链路，不是简单向量召回：

- 文档切分支持 7 种策略：固定切分、滑动窗口、递归切分、结构感知、语义切分、父子块切分、内容类型感知。
- 系统会按文档类型和场景自动选择切分方式。
- 检索后会做 rerank，再进入答案生成。
- 这样既保留长文档结构，也提升召回准确率和答案质量。

## 核心链路

用户提问 -> 登录会话与权限 -> 意图识别 -> 订单查询 / RAG / 敏感操作 -> Trace 记录 -> Elasticsearch -> AgentInsight 评测 -> bad case / 点踩反馈。

## 我负责的内容

- 独立设计整个 Agent 流程和模块边界。
- 完成 Java 侧 Planner、意图识别、RAG、工具编排和人工确认。
- 完成 Trace、反馈、bad case 和评测对接。
- 完成本地启动、联调和演示路径整理。

## 技术栈

Java、Spring Boot、Spring AI Alibaba StateGraph、MySQL、Redis、Milvus、RocketMQ、Elasticsearch、MCP。

## 重点

- 规则优先是为了稳定和安全，不把关键判定完全交给模型。
- RAG 要先做 chunk 策略，再做检索和重排，召回质量才会稳定。
- Trace 和 bad case 让问题可回放、可评测、可持续优化。

## 可演示场景

1. 普通订单查询，直接走确定性工具。
2. 规则问答，展示 chunk 切分、RAG 命中、重排和 Trace。
3. 退款或取消，展示人工确认、工具调用和工单落库。

## 
