package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.BalanceRules;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class ProcessTransactionService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final TransactionRepositoryPort transactions;
    private final AccountOperationLockPort accountLock;
    private final BalanceRules rules = new BalanceRules();

    public ProcessTransactionService (TransactionRepositoryPort transactions, AccountOperationLockPort accountLock){
        this.transactions = transactions;
        this.accountLock = accountLock;
    }

    @Transactional
    public Transaction process(Transaction candidate, AccountBalance account, String rejectionReason){
        if (rejectionReason != null)
            return transactions.save(candidate.reject(rejectionReason));

        boolean isDeposit = candidate.getType() == TransactionType.DEPOSIT;

        if (!isDeposit && account == null) {
            return transactions.save(
                    candidate.reject("Conta de saldo não encontrada")
            );
        }

        Long userId = candidate.getOriginUserId();
        accountLock.lockForUser(userId);

        if (!isDeposit) {
            LocalDate transactionDay = LocalDate.ofInstant(
                    candidate.getCreationDate(), ZONE
            );
            Instant start = transactionDay.atStartOfDay(ZONE).toInstant();
            Instant end = transactionDay.plusDays(1)
                    .atStartOfDay(ZONE).toInstant();

            var approved = transactions.findApprovedByOriginUserId(userId);
            var todayOutflows =
                    transactions.findApprovedOutflowsByOriginUserIdBetween(
                            userId, start, end
                    );
            var receivedTransfers =
                    transactions.findApprovedIncomingTransfersByUserId(userId);

            var summary = rules.calculate(
                    account, approved, todayOutflows, receivedTransfers
            );
            var decision = rules.evaluateOutflow(
                    account, summary, candidate.getAmount()
            );

            if (!decision.approved()) {
                return transactions.save(
                        candidate.reject(decision.rejectionReason())
                );
            }
        }

        return transactions.save(candidate);
    }
}
