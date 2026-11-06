package com.beca.transactions.adapter.out.mockapi;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.port.AccountBalancePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class MockApiAccountBalanceAdapter implements AccountBalancePort {
    private final RestClient client;

    public MockApiAccountBalanceAdapter(@Value("${mock-api.base-url}") String baseUrl){
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);

        this.client=RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Optional<AccountBalance> findByUserId(Long userId) {
        if(userId==null || userId<=0) throw new IllegalArgumentException("Id do usuário inválido");

        List<MockAccountResponse> accounts;

        try {
            accounts = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/accounts")
                            .queryParam("userId", userId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<MockAccountResponse>>() {});
        } catch (HttpClientErrorException.NotFound ex) {
            try {
                accounts = client.get()
                        .uri("/accounts")
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<MockAccountResponse>>() {});
            } catch (RestClientException fallbackEx) {
                throw new ExternalServiceUnavailableException(
                        "MockAPI de saldo e limites", fallbackEx
                );
            }
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(
                    "MockAPI de saldo e limites", ex
            );
        }

        if(accounts==null) throw invalidResponse("Resposta vazia");

        List<MockAccountResponse> matches = accounts.stream()
                .filter(account -> account!=null && userId.equals(account.userId()))
                .toList();

        if(matches.isEmpty()) return Optional.empty();
        if(matches.size()>1) throw invalidResponse("Mais de uma conta para o mesmo usuário");

        MockAccountResponse account = matches.getFirst();
        if(!"BRL".equals(account.currency())) throw invalidResponse("Moeda ausente ou diferente de BRL");

        try{
            return Optional.of(new AccountBalance(
                    account.userId(),
                    account.balance(),
                    account.dailyLimit(),
                    account.transactionLimit()
            ));
        } catch (IllegalArgumentException ex) {
            throw new ExternalServiceUnavailableException("MockAPI de saldo e limites", ex);
        }
    }

    private ExternalServiceUnavailableException invalidResponse(String reason) {
        return new ExternalServiceUnavailableException(
                "MockAPI de saldo e limites",
                new IllegalStateException(reason)
        );
    }

    private record MockAccountResponse(
            String id,
            Long userId,
            BigDecimal balance,
            BigDecimal dailyLimit,
            BigDecimal transactionLimit,
            String currency
    ) {}
}
