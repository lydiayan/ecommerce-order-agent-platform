package com.example.mallordermilvusrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档 catalog：文件名 → metadata，上传/导入时自动解析，无需每次传参。
 * <p>
 * catalog 使用 List 而非 Map，避免文件名中的 "." 在配置绑定时被 Spring 误解析为嵌套路径。
 */
@ConfigurationProperties(prefix = "rag")
public class RagDocumentProperties {

    /** classpath 下 PDF 目录，如 data */
    private String dataDir = "data";

    /** 文档 metadata 列表 */
    @NestedConfigurationProperty
    private List<CatalogEntry> catalog = new ArrayList<>();

    /** 文本分块参数 */
    @NestedConfigurationProperty
    private ChunkProperties chunk = new ChunkProperties();

    /** Qwen rerank 重排序配置 */
    @NestedConfigurationProperty
    private RerankProperties rerank = new RerankProperties();

    /** RAG 问答（/ask）配置 */
    @NestedConfigurationProperty
    private AskProperties ask = new AskProperties();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public List<CatalogEntry> getCatalog() {
        return catalog;
    }

    public void setCatalog(List<CatalogEntry> catalog) {
        this.catalog = catalog != null ? catalog : new ArrayList<>();
    }

    public ChunkProperties getChunk() {
        return chunk;
    }

    public void setChunk(ChunkProperties chunk) {
        this.chunk = chunk != null ? chunk : new ChunkProperties();
    }

    public RerankProperties getRerank() {
        return rerank;
    }

    public void setRerank(RerankProperties rerank) {
        this.rerank = rerank != null ? rerank : new RerankProperties();
    }

    public AskProperties getAsk() {
        return ask;
    }

    public void setAsk(AskProperties ask) {
        this.ask = ask != null ? ask : new AskProperties();
    }

    /** 按文件名索引，供运行时查找 */
    public Map<String, CatalogEntry> catalogByFilename() {
        Map<String, CatalogEntry> map = new LinkedHashMap<>();
        for (CatalogEntry entry : catalog) {
            if (entry.getFilename() != null && !entry.getFilename().isBlank()) {
                map.put(entry.getFilename(), entry);
            }
        }
        return map;
    }

    public static class CatalogEntry {

        private String filename;
        private String department;
        private String role;
        private String version;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    public static class ChunkProperties {

        /** 每块目标 token 数 */
        private int chunkSize = 250;

        /** 仅在断句点切分前，chunk 至少达到的字符数（中文 PDF 建议 80~120，默认 350 过大） */
        private int minChunkSizeChars = 80;

        /** 短于该字符数的块丢弃 */
        private int minChunkLengthToEmbed = 5;

        private int maxNumChunks = 10000;

        private boolean keepSeparator = true;

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getMinChunkSizeChars() {
            return minChunkSizeChars;
        }

        public void setMinChunkSizeChars(int minChunkSizeChars) {
            this.minChunkSizeChars = minChunkSizeChars;
        }

        public int getMinChunkLengthToEmbed() {
            return minChunkLengthToEmbed;
        }

        public void setMinChunkLengthToEmbed(int minChunkLengthToEmbed) {
            this.minChunkLengthToEmbed = minChunkLengthToEmbed;
        }

        public int getMaxNumChunks() {
            return maxNumChunks;
        }

        public void setMaxNumChunks(int maxNumChunks) {
            this.maxNumChunks = maxNumChunks;
        }

        public boolean isKeepSeparator() {
            return keepSeparator;
        }

        public void setKeepSeparator(boolean keepSeparator) {
            this.keepSeparator = keepSeparator;
        }
    }

    public static class RerankProperties {

        /** 是否启用 qwen3-rerank 重排序 */
        private boolean enabled = true;

        private String model = "qwen3-rerank";

        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-api/v1/reranks";

        /** Milvus 召回倍数：实际召回 topK × candidateMultiplier 条，再 rerank 截断 */
        private int candidateMultiplier = 4;

        /** rerank 后最低相关性分数（0~1），低于则丢弃 */
        private double minScore = 0.0;

        /**
         * 排序任务说明。问答检索默认 instruct；中文场景可改为中文描述。
         */
        private String instruct = "Given a web search query, retrieve relevant passages that answer the query.";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int candidateMultiplier) {
            this.candidateMultiplier = candidateMultiplier;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public String getInstruct() {
            return instruct;
        }

        public void setInstruct(String instruct) {
            this.instruct = instruct;
        }
    }

    public static class AskProperties {

        private String model = "qwen-plus";

        private double temperature = 0.3;

        /** 送入 LLM 的参考资料条数上限 */
        private int contextTopK = 5;

        private String systemPrompt = """
                你是企业知识库问答助手。请严格根据用户消息中「参考资料」回答问题。
                要求：
                1. 仅使用参考资料中的信息，不要编造。
                2. 若参考资料不足以回答，请明确回复「知识库中未找到相关信息」。
                3. 回答简洁、准确，可使用条目列表。
                4. 不要透露参考资料的编号或内部字段名。
                """;

        /** Prompt 模板版本，写入 trace 便于对比实验 */
        private String promptVersion = "v1";

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getContextTopK() {
            return contextTopK;
        }

        public void setContextTopK(int contextTopK) {
            this.contextTopK = contextTopK;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
        }
    }
}
