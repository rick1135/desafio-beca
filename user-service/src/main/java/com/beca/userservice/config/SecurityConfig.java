package com.beca.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain internalAuthFilterChain(HttpSecurity http) throws Exception{
        http.securityMatcher("/internal/auth/**")
                .authorizeHttpRequests(authorize->authorize
                        .requestMatchers(HttpMethod.POST, "/internal/auth/validate")
                        .permitAll().anyRequest().denyAll())
                .csrf(csrf->csrf.disable())
                .sessionManagement(session->session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ));
        return http.build();
    }
}
