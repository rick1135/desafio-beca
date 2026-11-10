package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.BalanceRules;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
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
        boolean correction = candidate.isCorrection();
        Long userId = candidate.getOriginUserId();
        Transaction original = null;
        if (correction) {
            accountLock.lockForUser(userId);
            Long originalId = candidate.getOriginalTransactionId();
            original = transactions.findById(originalId)
                    .orElseThrow(() -> new TransactionNotFoundException(originalId));
            if (!original.getOriginUserId().equals(userId)) {
                throw new ForbiddenTransactionAccessException();
            }
            candidate.validateCorrectionOf(original);
            if (transactions.existsByOriginalTransactionIdAndStatus(originalId, TransactionStatus.APPROVED)) {
                throw new InvalidTransactionException("Esta transação já foi corrigida");
            }
        }
        if (rejectionReason != null)
            return transactions.save(candidate.reject(rejectionReason));

        boolean isDeposit = candidate.getType() == TransactionType.DEPOSIT;

        if ((!isDeposit || correction) && account == null) {
            return transactions.save(
                    candidate.reject("Conta de saldo não encontrada")
            );
        }

        if (!correction) {
            accountLock.lockForUser(userId);
        }

        if (!isDeposit || correction) {
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
            var decision = correction
                    ? rules.evaluateCorrection(account, summary, original, candidate,
                            !original.getCreationDate().isBefore(start)
                                    && original.getCreationDate().isBefore(end))
                    : rules.evaluateOutflow(account, summary, candidate.getAmount());

            if (!decision.approved()) {
                return transactions.save(
                        candidate.reject(decision.rejectionReason())
                );
            }
        }

        return transactions.save(candidate);
    }
}
