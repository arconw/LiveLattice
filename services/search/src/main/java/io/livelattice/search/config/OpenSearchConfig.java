package io.livelattice.search.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient openSearchClient(SearchProperties properties, ObjectMapper objectMapper) {
        URI uri = URI.create(properties.getOpensearchUrl());
        int port = uri.getPort() == -1 ? defaultPort(uri.getScheme()) : uri.getPort();
        HttpHost[] hosts = new HttpHost[] {
            new HttpHost(uri.getScheme(), uri.getHost(), port)
        };
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
            .builder(hosts)
            .setMapper(new JacksonJsonpMapper(objectMapper))
            .build();
        return new OpenSearchClient(transport);
    }

    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 9200;
    }
}
