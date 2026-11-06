package com.beca.transactions.domain.model;

import java.math.BigDecimal;
import java.util.List;

public class BalanceRules {
    public BalanceSummary calculate(
            AccountBalance account,
            List<Transaction> approvedTransactions,
            List<Transaction> todayOutflows,
            List<Transaction> receivedTransfers
    ){
        BigDecimal effectiveBalance = account.openingBalance();

        for(Transaction transaction:approvedTransactions){
            if(transaction.getStatus()!=TransactionStatus.APPROVED) continue;
            if(transaction.getType()==TransactionType.DEPOSIT) effectiveBalance=effectiveBalance.add(transaction.getAmount());
            else if (isOutflow(transaction.getType())) effectiveBalance=effectiveBalance.subtract(transaction.getAmount());
        }

        BigDecimal dailySpent = BigDecimal.ZERO;

        for (Transaction transaction:todayOutflows){
            if (transaction.getStatus() == TransactionStatus.APPROVED && isOutflow(transaction.getType()))
                dailySpent = dailySpent.add(transaction.getAmount());
        }

        for (Transaction transaction : receivedTransfers) {
            if (transaction.getStatus() == TransactionStatus.APPROVED
                    && transaction.getType() == TransactionType.TRANSFER
                    && account.userId().equals(transaction.getDestinationUserId())) {
                effectiveBalance = effectiveBalance.add(transaction.getAmount());
            }
        }

        return new BalanceSummary(effectiveBalance, dailySpent);
    }

    private boolean isOutflow(TransactionType type) {
        return type == TransactionType.WITHDRAW
                || type == TransactionType.TRANSFER
                || type == TransactionType.PURCHASE;
    }

    public record BalanceSummary(
            BigDecimal effectiveBalance,
            BigDecimal dailySpent
    ) {}

    public OutflowDecision evaluateOutflow(
            AccountBalance account,
            BalanceSummary summary,
            BigDecimal amount
    ) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }
        if (amount.compareTo(summary.effectiveBalance()) > 0) {
            return new OutflowDecision(false, "Saldo insuficiente");
        }
        if (amount.compareTo(account.transactionLimit()) > 0) {
            return new OutflowDecision(false, "Limite por transação excedido");
        }
        if (summary.dailySpent().add(amount).compareTo(account.dailyLimit()) > 0) {
            return new OutflowDecision(false, "Limite diário excedido");
        }
        return new OutflowDecision(true, null);
    }

    public record OutflowDecision(
            boolean approved,
            String rejectionReason
    ) {}

}
