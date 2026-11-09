# RAG 文档切分

本模块通过 `AbstractRagDocumentSplitter` 和 `DocumentSplitterRegistry` 提供七种切分策略：

- `FIXED_SIZE`：严格按照估算 Token 数切分固定窗口。
- `SLIDING_WINDOW`：固定窗口切分，并在相邻窗口之间保留 Token 重叠。
- `RECURSIVE`：依次尝试段落、换行、句子、分句和空白等自然边界。
- `STRUCTURE_AWARE`：识别中文章节/文章标题、Markdown 标题、数字编号标题以及 HTML DOM 结构。
- `SEMANTIC`：根据相邻句子的 Embedding 距离，并结合最小、目标和最大 Token 限制确定边界。
- `PARENT_CHILD`：在同一个 Milvus 集合中生成具有上下文关系的父块和子块。
- `CONTENT_TYPE_AWARE`：根据内容类型，将普通文本、PDF、Markdown、HTML、FAQ、表格和代码路由到合适的切分逻辑。

默认策略为 `CONTENT_TYPE_AWARE`。服务端统一管理的限制参数配置在 `rag.yml` 的
`rag.chunk` 下。导入请求只允许覆盖 `documentId`、`strategy` 和 `contentType`。
PDF 的 multipart 上传接口还支持可选的 `documentId` 和 `strategy` 参数；PDF 的内容类型始终固定为 `PDF`。

## 策略配置

Chunk 参数按使用它们的策略分组。通用的 `strategy` 和 `max-num-chunks` 参数同时作用于注册表和所有切分器。
`STRUCTURE_AWARE` 与 `CONTENT_TYPE_AWARE` 复用递归切分配置，因为它们会把超出限制的区域委托给递归切分器处理。

```yaml
rag:
  chunk:
    strategy: CONTENT_TYPE_AWARE
    max-num-chunks: 10000
    fixed-size:
      max-tokens: 250
    sliding-window:
      max-tokens: 250
      overlap-tokens: 50
    recursive:
      min-tokens: 80
      max-tokens: 400
      overlap-tokens: 40
    semantic:
      min-tokens: 100
      target-tokens: 250
      max-tokens: 500
      batch-size: 64
      max-sentences: 2000
      boundary-percentile: 0.85
    parent-child:
      parent-tokens: 800
      child-tokens: 200
      child-overlap-tokens: 40
```

如果 Token 大小之间的关系不合法，应用会在启动时失败。已经移除的扁平配置项，例如
`chunk-size`、`recursive-max-tokens` 和 `semantic-target-tokens`，也会被拒绝，而不是被静默忽略。

## Milvus v3 迁移

自定义 Schema 由 `RagService` 负责维护，通用的 Spring AI Schema 初始化已禁用。
默认集合为 `mall_rag_v3`，也可以通过 `RAG_COLLECTION_NAME` 覆盖。服务不会修改或删除旧的
`mall_rag_collection` 集合。

已有 Chunk 无法可靠地升级，因为其中不包含原始文档结构或父子关系。请通过导入接口重新提交原始文本或 PDF。
传入稳定的 `documentId` 后，重复导入将保持幂等，并在文档替换时删除旧的失效 Chunk。

## 验证

普通测试使用确定性的模拟 Embedding 模型：

```bash
./mvnw -pl mall-order-milvus-rag -am test
```

启动本地基础设施后，运行独立的 Milvus 集成测试：

```bash
./scripts/dev-up.sh
RUN_MILVUS_IT=true ./mvnw -pl mall-order-milvus-rag -am \
  -Dtest=RagMilvusIntegrationTest \
  -Dsurefire.excludes= \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

集成测试只会创建并删除一个名称唯一的临时集合，不会调用 DashScope，也不会修改业务集合。
