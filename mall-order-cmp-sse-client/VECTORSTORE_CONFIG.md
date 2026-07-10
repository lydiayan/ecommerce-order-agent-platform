# VectorStore 配置说明

## 概述

VectorStore（向量存储）用于存储和检索文档的向量表示，是 RAG（检索增强生成）系统的核心组件。本项目使用 Elasticsearch 作为向量存储。

## 配置方式

### 1. application.yml 配置

```yaml
spring:
  ai:
    # Elasticsearch 连接配置
    elasticsearch:
      uris: http://127.0.0.1:9200  # Elasticsearch 服务器地址
      username:                    # 可选：用户名（如果需要认证）
      password:                     # 可选：密码（如果需要认证）
    
    # VectorStore 配置
    vectorstore:
      elasticsearch:
        # 是否自动初始化 schema（索引结构）
        initialize-schema: true
        # 向量索引名称
        index-name: mall-vector
        # 相似度计算方式
        similarity: cosine  # 可选值：cosine, dot_product, l2_norm
        # 向量维度（必须与 embedding 模型的输出维度匹配）
        dimensions: 1536    # qwen2.5-vl-embedding 的维度是 1536
```

### 2. Java 配置类

`ElasticsearchConfig.java` 提供了完整的配置：

```java
@Configuration
public class ElasticsearchConfig {
    
    // 1. 配置 Elasticsearch RestClient
    @Bean
    public RestClient restClient() {
        // 解析配置的 URL
        String[] urlParts = url.split("://");
        String protocol = urlParts[0];  // http 或 https
        String hostAndPort = urlParts[1];
        String[] hostPortParts = hostAndPort.split(":");
        String host = hostPortParts[0];
        int port = Integer.parseInt(hostPortParts[1]);
        
        return RestClient.builder(
            new HttpHost(host, port, protocol)
        ).build();
    }
    
    // 2. 配置 ElasticsearchVectorStore
    @Bean
    @Qualifier("esVectorStore")
    public ElasticsearchVectorStore vectorStore(
            RestClient restClient, 
            EmbeddingModel embeddingModel) {
        
        // 配置选项
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setSimilarity(similarityFunction);  // 相似度函数
        options.setIndexName(indexName);             // 索引名称
        options.setDimensions(dimensions);           // 向量维度
        
        // 构建 VectorStore
        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)              // 自动初始化索引
                .batchingStrategy(new TokenCountBatchingStrategy())  // 批处理策略
                .build();
    }
}
```

## 配置参数说明

### Elasticsearch 连接参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `spring.ai.elasticsearch.uris` | Elasticsearch 服务器地址 | `http://127.0.0.1:9200` |
| `spring.ai.elasticsearch.username` | 用户名（可选） | `elastic` |
| `spring.ai.elasticsearch.password` | 密码（可选） | `password` |

### VectorStore 参数

| 参数 | 说明 | 可选值 | 默认值 |
|------|------|--------|--------|
| `initialize-schema` | 是否自动创建索引结构 | `true`, `false` | `true` |
| `index-name` | 向量索引名称 | 自定义字符串 | - |
| `similarity` | 相似度计算方式 | `cosine`, `dot_product`, `l2_norm` | `cosine` |
| `dimensions` | 向量维度 | 整数（需匹配 embedding 模型） | - |

### 相似度函数说明

- **cosine（余弦相似度）**：最常用，适合大多数场景
  - 范围：[-1, 1]
  - 值越大越相似
  
- **dot_product（点积）**：计算速度快
  - 需要向量归一化
  
- **l2_norm（L2 范数/欧氏距离）**：基于距离
  - 值越小越相似

## Embedding 模型与维度匹配

确保 `dimensions` 配置与使用的 embedding 模型输出维度一致：

| Embedding 模型 | 维度 |
|----------------|------|
| `qwen2.5-vl-embedding` | 1536 |
| `text-embedding-v1` | 1536 |
| `text-embedding-v2` | 1024 |
| `text-embedding-v3` | 1024 |

## 使用示例

### 在 Controller 中注入使用

```java
@RestController
public class MallAgentController {
    
    private final ElasticsearchVectorStore vectorStore;
    
    public MallAgentController(
            @Qualifier("esVectorStore") ElasticsearchVectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }
    
    // 使用 VectorStore 进行向量检索
    public void search() {
        // 添加文档
        vectorStore.add(List.of(
            new Document("文档内容", Map.of("metadata", "value"))
        ));
        
        // 相似度搜索
        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.query("查询文本")
                .withTopK(10)
                .withSimilarityThreshold(0.7)
        );
    }
}
```

## 常见问题

### 1. 维度不匹配

**错误**：`dimensions mismatch`

**解决**：检查 `dimensions` 配置是否与 embedding 模型输出维度一致。

### 2. 索引不存在

**错误**：`index_not_found_exception`

**解决**：确保 `initialize-schema: true`，或手动创建索引。

### 3. 连接失败

**错误**：`Connection refused`

**解决**：
- 检查 Elasticsearch 是否启动
- 检查 `uris` 配置是否正确
- 检查防火墙设置

## 最佳实践

1. **生产环境**：设置 `initialize-schema: false`，手动管理索引
2. **性能优化**：根据数据量调整批处理策略
3. **安全配置**：生产环境使用认证和 HTTPS
4. **监控**：监控索引大小和查询性能
