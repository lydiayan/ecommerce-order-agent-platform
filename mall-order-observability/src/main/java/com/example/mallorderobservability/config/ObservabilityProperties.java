package com.example.mallorderobservability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "observability")
public class ObservabilityProperties {

    private boolean enabled = true;
    private String serviceName = "mall-order-milvus-rag";

    @NestedConfigurationProperty
    private TraceProperties trace = new TraceProperties();

    @NestedConfigurationProperty
    private ProducerProperties producer = new ProducerProperties();

    @NestedConfigurationProperty
    private ConsumerProperties consumer = new ConsumerProperties();

    @NestedConfigurationProperty
    private ElasticsearchProperties elasticsearch = new ElasticsearchProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public TraceProperties getTrace() {
        return trace;
    }

    public void setTrace(TraceProperties trace) {
        this.trace = trace;
    }

    public ProducerProperties getProducer() {
        return producer;
    }

    public void setProducer(ProducerProperties producer) {
        this.producer = producer;
    }

    public ConsumerProperties getConsumer() {
        return consumer;
    }

    public void setConsumer(ConsumerProperties consumer) {
        this.consumer = consumer;
    }

    public ElasticsearchProperties getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(ElasticsearchProperties elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    public static class TraceProperties {
        private String topic = "rag-trace-events";
        private String tag = "trace";

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }
    }

    public static class ProducerProperties {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ConsumerProperties {
        private boolean enabled = false;
        private String group = "rag-trace-consumer";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }

    public static class ElasticsearchProperties {
        private String uris = "http://127.0.0.1:9200";
        private String username;
        private String password;
        private String index = "rag-traces";

        public String getUris() {
            return uris;
        }

        public void setUris(String uris) {
            this.uris = uris;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getIndex() {
            return index;
        }

        public void setIndex(String index) {
            this.index = index;
        }
    }
}
