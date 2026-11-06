package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.BalanceRules;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.application.exception.AccountBalanceNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class GetBalanceService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final AccountBalancePort accounts;
    private final TransactionRepositoryPort transactions;
    private final BalanceRules rules = new BalanceRules();

    public GetBalanceService(AccountBalancePort accounts, TransactionRepositoryPort transactions){
        this.accounts = accounts;
        this.transactions=transactions;
    }

    public BalanceDetails getBalance(Long userId){
        AccountBalance account = accounts.findByUserId(userId)
                .orElseThrow(() -> new AccountBalanceNotFoundException(userId));

        LocalDate today = LocalDate.now(ZONE);
        Instant start = today.atStartOfDay(ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZONE).toInstant();

        var approved = transactions.findApprovedByOriginUserId(userId);

        var todayOutflows = approved.stream()
                .filter(t -> !t.getCreationDate().isBefore(start) && t.getCreationDate().isBefore(end))
                .filter(t -> t.getType() == TransactionType.WITHDRAW
                        || t.getType() == TransactionType.TRANSFER
                        || t.getType() == TransactionType.PURCHASE)
                .toList();

        var summary = rules.calculate(account, approved, todayOutflows);

        return new BalanceDetails(
                userId, account.openingBalance(), summary.effectiveBalance(), summary.dailySpent(),
                account.dailyLimit(), account.transactionLimit()
        );
    }

    public record BalanceDetails(
            Long userId,
            BigDecimal openingBalance,
            BigDecimal effectiveBalance,
            BigDecimal dailySpent,
            BigDecimal dailyLimit,
            BigDecimal transactionLimit
    ) {}
}
