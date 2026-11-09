package com.beca.transactions.adapter.out.users;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AuthenticatedUser;
import com.beca.transactions.domain.port.UserAuthenticationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Component
public class UserServiceAuthenticationAdapter implements UserAuthenticationPort {
    private final RestClient client;
    private final String internalAuthKey;

    public UserServiceAuthenticationAdapter(@Value("${user-service.base-url}") String baseUrl,
                                            @Value("${INTERNAL_AUTH_KEY:}") String internalAuthKey){
        Assert.hasText(internalAuthKey, "Configure a variável INTERNAL_AUTH_KEY para autenticar no MS1");
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);

        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalAuthKey = internalAuthKey;
    }

    @Override
    public Optional<AuthenticatedUser> authenticate(String email, String senha) {
        try {
            AuthResponse response = client.post()
                    .uri("/internal/auth/validate")
                    .header("X-Internal-Auth-Key", internalAuthKey)
                    .body(new AuthRequest(email, senha))
                    .retrieve()
                    .body(AuthResponse.class);

            if (response == null
                    || response.id() == null
                    || response.id() <= 0
                    || response.email() == null
                    || response.email().isBlank()) {
                throw new ExternalServiceUnavailableException(
                        "MS1 de usuários",
                        new IllegalStateException("Resposta de autenticação inválida")
                );
            }

            return Optional.of(new AuthenticatedUser(
                    response.id(),
                    response.nome(),
                    response.email()
            ));

        } catch (HttpClientErrorException.Unauthorized
                 | HttpClientErrorException.BadRequest ex) {
            return Optional.empty();

        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException("MS1 de usuários", ex);
        }
    }

    private record AuthRequest(String email, String senha) {}

    private record AuthResponse(Long id, String nome, String email) {}
}
