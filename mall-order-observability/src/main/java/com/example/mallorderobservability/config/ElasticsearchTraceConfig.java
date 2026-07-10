package com.example.mallorderobservability.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.mallorderobservability.storage.ElasticsearchTraceRepository;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "observability.consumer", name = "enabled", havingValue = "true")
public class ElasticsearchTraceConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchTraceConfig.class);

    @Bean(destroyMethod = "close")
    public RestClient traceRestClient(ObservabilityProperties properties) {
        URI uri = URI.create(properties.getElasticsearch().getUris());
        RestClientBuilder builder = RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()));

        String username = properties.getElasticsearch().getUsername();
        String password = properties.getElasticsearch().getPassword();
        if (username != null && !username.isBlank()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password != null ? password : ""));
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        log.info("Elasticsearch trace client connected to {}", properties.getElasticsearch().getUris());
        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient traceRestClient) {
        ElasticsearchTransport transport = new RestClientTransport(traceRestClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    @Bean
    public ElasticsearchTraceRepository elasticsearchTraceRepository(ElasticsearchClient elasticsearchClient,
                                                                       ObservabilityProperties properties) {
        return new ElasticsearchTraceRepository(elasticsearchClient, properties);
    }
}
