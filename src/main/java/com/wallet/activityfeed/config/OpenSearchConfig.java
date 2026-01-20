package com.wallet.activityfeed.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenSearch client configuration
 * Configured for local OpenSearch in development
 */
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Value("${opensearch.username:admin}")
    private String username;

    @Value("${opensearch.password:admin}")
    private String password;

    @Bean(destroyMethod = "close")
    public RestHighLevelClient openSearchClient() {
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(username, password)
        );

        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, scheme))
                        .setHttpClientConfigCallback(httpClientBuilder ->
                                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
        );
    }
}
