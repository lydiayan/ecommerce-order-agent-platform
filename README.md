# 使用Spring AI Alibaba实现的智能客服 

> Spring AI Alibaba Repo: https://github.com/alibaba/spring-ai-alibaba
>
> Spring AI Alibaba Website:  https://java2ai.com
>
> Spring AI Alibaba Website Repo: https://github.com/springaialibaba/spring-ai-alibaba-website



EcommSpringBot

EcommSpringBot 是一个基于 SpringAI Alibaba 的企业级智能客服助手系统，支持用户通过自然语言交互完成订单查询、自动取消、规则问答等操作，融合微服务架构、RAG 技术、Redis 聊天上下文记忆与 Elasticsearch 向量数据库，实现私有化可扩展的智能体服务平台。

🌟 项目亮点
•	🧠 基于 SpringAI Alibaba 实现企业智能体对话能力（支持通义千问/OpenAI）
•	🔗 模块化设计：订单服务、中台服务、SSE方式连接MCP服务、RAG 知识库清晰分离
•	🧾 支持自然语言查询订单、自动取消订单
•	📚 使用 Elasticsearch 构建企业知识库，支持 RAG 检索增强
•	🧠 使用 Redis 实现上下文记忆功能，支持多轮对话

🏗️ 模块结构

EcommSpringBot/
├── mall-order/                  # 核心订单服务（Spring Boot + MySQL）
├── mall-order-cmp-server/       # CMP 中台服务（封装订单能力）
├── mall-order-cmp_sso-client/   # 提供 Controller 接口 + Redis 聊天上下文支持
├── mall-order-es-rag/          # 向量知识库模块（退换货规则文档 → ES 向量）
├── mall-order-graph-server/     # MCP 图计算服务（基于 Spring AI Alibaba）
└── README.md


📌 功能特性

✅ 1. 自然语言操作订单服务
•	用户可使用自然语言对话，如：
•	“我想查询 用户USER1005 的订单”
•	“帮我取消用户USER1005 的订单”

通过 Spring Ai Alibaba Function Calling 自动匹配接口并调用 mall-order 服务完成。

⸻

✅ 2. SSE 实时回复 + Redis 上下文记忆
•	使用 SSE（Server-Sent Events）建立流式连接
•	基于 Redis 实现用户对话上下文记忆
•	支持连续上下文查询、简洁自然的人机交互体验

⸻

✅ 3. 向量检索增强（RAG）+ ES
•	退换货规则、发票须知等内容整理后构建知识库，提供动态修改知识库内容
•	使用 text-embedding-v1 模型生成向量，存入 Elasticsearch
•	用户提问如“我的商品退货时间是多久？”可自动语义匹配文档内容并回答


🚀 快速启动

前提条件
•	JDK 17+
•	Maven 3.9+
•	Redis
•	MySQL
•	Elasticsearch 8.11+

启动顺序

# 启动 mall-order 服务
cd mall-order && mvn spring-boot:run

# 启动 mall-order-cmp-server
cd ../mall-order-cmp-server && mvn spring-boot:run

# 启动 sso-client 接口层（含AI智能体接口）
cd ../mall-order-cmp_sso-client && mvn spring-boot:run

# 启动 es-rag 服务（用于知识库管理）
cd ../mall-order-es-rag && mvn spring-boot:run

📖
📄 LICENSE

本项目基于 Apache 2.0 License 开源，可自由使用、修改与分发。

⸻

如果你觉得该项目有价值，欢迎 Star⭐️ 或 Fork🍴！

📬 联系作者：996766130@qq.com
## 前端页面
![img_1.png](img_1.png)
