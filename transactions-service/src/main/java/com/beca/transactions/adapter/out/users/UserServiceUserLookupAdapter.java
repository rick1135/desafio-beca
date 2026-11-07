package com.beca.transactions.adapter.out.users;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.port.UserLookupPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserServiceUserLookupAdapter implements UserLookupPort {
    private final RestClient client;

    public UserServiceUserLookupAdapter(@Value("${user-service.base-url}") String baseUrl) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public boolean existsById(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("ID do usuário inválido");
        }
        try {
            UserResponse user = client.get().uri("/users/{id}", userId)
                    .retrieve().body(UserResponse.class);
            if (user == null || !userId.equals(user.id())) {
                throw new ExternalServiceUnavailableException("MS1 de usuários",
                        new IllegalStateException("Resposta de usuário inválida"));
            }
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException("MS1 de usuários", ex);
        }
    }

    private record UserResponse(Long id) {}
}
