package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.model.ExchangeCurrency;
import com.beca.transactions.domain.model.ExchangeRate;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.ExchangeRatePort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import com.beca.transactions.domain.port.UserLookupPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@Service
public class CreateTransactionService {
    private static final String MOCK_UNAVAILABLE =
            "Serviço externo de consulta de saldo e limites temporariamente indisponível";

    private final AccountBalancePort accounts;
    private final UserLookupPort users;
    private final ProcessTransactionService processTransactionService;
    private final ExchangeRatePort exchangeRates;
    private final TransactionRepositoryPort transactions;

    public CreateTransactionService(AccountBalancePort accounts, UserLookupPort users,
                                    ProcessTransactionService processTransactionService,
                                    ExchangeRatePort exchangeRates, TransactionRepositoryPort transactions) {
        this.accounts = accounts;
        this.users = users;
        this.processTransactionService = processTransactionService;
        this.exchangeRates = exchangeRates;
        this.transactions = transactions;
    }

    public Transaction create(Long originUserId, TransactionType type, BigDecimal amount,
            String description, String category, Long destinationUserId, Long originalTransactionId
    ){
        return create(originUserId, type, amount, description, category,
                destinationUserId, originalTransactionId, null);
    }

    public Transaction create(Long originUserId, TransactionType type, BigDecimal amount,
            String description, String category, Long destinationUserId,
            Long originalTransactionId, String correctionReason
    ) {
        if (originalTransactionId != null && correctionReason == null) {
            throw new InvalidTransactionException("Motivo da correção deve ter entre 1 e 255 caracteres");
        }

        Instant now = Instant.now();
        Transaction candidate = new Transaction(
                null, type, amount, category, originUserId, destinationUserId,
                TransactionStatus.APPROVED, null, originalTransactionId, description, now,
                List.of(), correctionReason
        );

        if (originalTransactionId != null) {
            Transaction original = transactions.findById(originalTransactionId)
                    .orElseThrow(() -> new TransactionNotFoundException(originalTransactionId));
            if (!original.getOriginUserId().equals(originUserId)) {
                throw new ForbiddenTransactionAccessException();
            }
        }

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
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var usdTask = executor.submit(() -> exchangeRates.findByCurrencyAndDate(ExchangeCurrency.USD, date));
            var eurTask = executor.submit(() -> exchangeRates.findByCurrencyAndDate(ExchangeCurrency.EUR, date));
            Optional<ExchangeRate> usd = usdTask.get();
            Optional<ExchangeRate> eur = eurTask.get();
            if (usd.isEmpty() || eur.isEmpty()
                    || usd.get().currency() != ExchangeCurrency.USD
                    || eur.get().currency() != ExchangeCurrency.EUR) {
                return List.of();
            }
            return List.of(usd.get(), eur.get());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (ExecutionException ex) {
            return List.of();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
