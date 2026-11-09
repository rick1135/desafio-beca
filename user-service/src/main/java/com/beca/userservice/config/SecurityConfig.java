package com.beca.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain internalAuthFilterChain(
            HttpSecurity http,
            @Value("${INTERNAL_AUTH_KEY:}") String internalAuthKey
    ) throws Exception {
        Assert.hasText(internalAuthKey, "Configure INTERNAL_AUTH_KEY para habilitar a autenticação interna");
        byte[] expectedKey = internalAuthKey.getBytes(StandardCharsets.UTF_8);

        http.securityMatcher("/internal/auth/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/internal/auth/validate")
                        .access((authentication, context) -> {
                            String providedKey = context.getRequest().getHeader("X-Internal-Auth-Key");
                            boolean validKey = providedKey != null && MessageDigest.isEqual(
                                    expectedKey, providedKey.getBytes(StandardCharsets.UTF_8));
                            return new AuthorizationDecision(validKey);
                        })
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
