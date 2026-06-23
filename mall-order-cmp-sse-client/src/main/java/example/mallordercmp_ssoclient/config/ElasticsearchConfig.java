package example.mallordercmp_ssoclient.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


//@Configuration
public class ElasticsearchConfig {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Value("${spring.ai.elasticsearch.uris}")
    private String url;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name}")
    private String indexName;

    @Value("${spring.ai.vectorstore.elasticsearch.similarity}")
    private SimilarityFunction similarityFunction;

    @Value("${spring.ai.vectorstore.elasticsearch.dimensions}")
    private int dimensions;

    //@Bean
    public RestClient restClient() {
        String[] urlParts = url.split("://");
        String protocol = urlParts[0];
        String hostAndPort = urlParts[1];
        String[] hostPortParts = hostAndPort.split(":");
        String host = hostPortParts[0];
        int port = Integer.parseInt(hostPortParts[1]);

        // 创建 Elasticsearch RestClient
        return RestClient.builder(
                new HttpHost(host, port, protocol)
        ).build();
    }

/*    @Bean
    @Qualifier("esVectorStore")
    public ElasticsearchVectorStore vectorStore(@Qualifier("restClient") RestClient restClient, EmbeddingModel embeddingModel) {
        logger.info("创建es向量数据库");
        ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
        options.setSimilarity(similarityFunction);
        options.setIndexName(indexName);
        options.setDimensions(dimensions);

        return ElasticsearchVectorStore.builder(restClient, embeddingModel)
                .options(options)
                .initializeSchema(true)
                .batchingStrategy(new TokenCountBatchingStrategy())
                .build();
    }*/

}
