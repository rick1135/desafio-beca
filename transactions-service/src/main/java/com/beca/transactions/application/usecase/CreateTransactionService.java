package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.ExchangeRatePort;
import com.beca.transactions.domain.port.UserLookupPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
public class CreateTransactionService {
    private static final String MOCK_UNAVAILABLE =
            "Serviço externo de consulta de saldo e limites temporariamente indisponível";

    private final AccountBalancePort accounts;
    private final UserLookupPort users;
    private final ProcessTransactionService processTransactionService;
    private final ExchangeRatePort exchangeRates;

    public CreateTransactionService(AccountBalancePort accounts, UserLookupPort users,
                                    ProcessTransactionService processTransactionService,
                                    ExchangeRatePort exchangeRates) {
        this.accounts = accounts;
        this.users = users;
        this.processTransactionService = processTransactionService;
        this.exchangeRates = exchangeRates;
    }

    public Transaction create(Long originUserId, TransactionType type, BigDecimal amount,
            String description, String category, Long destinationUserId, Long originalTransactionId
    ){
        Instant now = Instant.now();
        Transaction candidate = new Transaction(
                null, type, amount, category, originUserId, destinationUserId,
                TransactionStatus.APPROVED, null, originalTransactionId, description, now
        );
        candidate = candidate.withExchangeRates(loadExchangeRates(now));
        String rejectionReason = null;
        AccountBalance account = null;

        if (type == TransactionType.TRANSFER) {
            try {
                if (!users.existsById(destinationUserId)) {
                    rejectionReason = "Usuário de destino não encontrado";
                }
            } catch (ExternalServiceUnavailableException ex) {
                rejectionReason = "Serviço de consulta de usuários temporariamente indisponível";
            }
        }

        if (type != TransactionType.DEPOSIT && rejectionReason == null) {
            try {
                account = accounts.findByUserId(originUserId).orElse(null);
            } catch (ExternalServiceUnavailableException ex) {
                rejectionReason = MOCK_UNAVAILABLE;
            }
        }

        return processTransactionService.process(candidate, account, rejectionReason);
    }

    private List<ExchangeRate> loadExchangeRates(Instant createdAt) {
        LocalDate date = LocalDate.ofInstant(createdAt, ZoneId.of("America/Sao_Paulo"));
        try {
            Optional<ExchangeRate> usd = exchangeRates.findByCurrencyAndDate(ExchangeCurrency.USD, date);
            Optional<ExchangeRate> eur = exchangeRates.findByCurrencyAndDate(ExchangeCurrency.EUR, date);
            if (usd.isEmpty() || eur.isEmpty()
                    || usd.get().currency() != ExchangeCurrency.USD
                    || eur.get().currency() != ExchangeCurrency.EUR) {
                return List.of();
            }
            return List.of(usd.get(), eur.get());
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
