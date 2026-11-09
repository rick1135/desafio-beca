package com.beca.transactions.adapter.out.users;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.port.UserLookupPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class UserServiceUserLookupAdapter implements UserLookupPort {
    private final RestClient client;

    public UserServiceUserLookupAdapter(@Qualifier("userServiceRestClient") RestClient client) {
        this.client = client;
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
