package com.beca.transactions.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class UserServiceClientConfig {
    @Bean("userServiceRestClient")
    public RestClient userServiceRestClient(
            @Value("${user-service.base-url}") String baseUrl,
            @Value("${user-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${user-service.read-timeout-ms}") int readTimeoutMs) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Os timeouts do MS1 devem ser positivos");
        }
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
