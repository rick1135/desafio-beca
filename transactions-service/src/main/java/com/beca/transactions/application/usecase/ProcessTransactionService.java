package com.beca.transactions.application.usecase;

import com.beca.transactions.domain.model.AccountBalance;
import com.beca.transactions.domain.model.BalanceRules;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.application.exception.ForbiddenTransactionAccessException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ProcessTransactionService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final TransactionRepositoryPort transactions;
    private final AccountOperationLockPort accountLock;
    private final AccountBalancePort accounts;
    private final BalanceRules rules = new BalanceRules();

    public ProcessTransactionService(TransactionRepositoryPort transactions, AccountOperationLockPort accountLock) {
        this(transactions, accountLock, null);
    }

    @Autowired
    public ProcessTransactionService(TransactionRepositoryPort transactions, AccountOperationLockPort accountLock,
                                   AccountBalancePort accounts) {
        this.transactions = transactions;
        this.accountLock = accountLock;
        this.accounts = accounts;
    }

    @Transactional
    public Transaction process(Transaction candidate, AccountBalance account, String rejectionReason) {
        return process(candidate, account, null, rejectionReason);
    }

    @Transactional
    public Transaction process(Transaction candidate, AccountBalance account,
                               AccountBalance recipientAccount, String rejectionReason) {
        boolean correction = candidate.isCorrection();
        Long userId = candidate.getOriginUserId();
        Transaction original = null;
        if (correction) {
            Long originalId = candidate.getOriginalTransactionId();
            original = transactions.findById(originalId)
                    .orElseThrow(() -> new TransactionNotFoundException(originalId));
            if (!original.getOriginUserId().equals(userId)) {
                throw new ForbiddenTransactionAccessException();
            }
            candidate.validateCorrectionOf(original);

            Set<Long> accountsToLock = new TreeSet<>();
            accountsToLock.add(userId);
            if (original.getType() == TransactionType.TRANSFER) {
                if (original.getDestinationUserId() != null) {
                    accountsToLock.add(original.getDestinationUserId());
                }
                if (candidate.getDestinationUserId() != null) {
                    accountsToLock.add(candidate.getDestinationUserId());
                }
            }

            for (Long accountId : accountsToLock) {
                accountLock.lockForUser(accountId);
            }

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

        if (correction && original.getType() == TransactionType.TRANSFER) {
            Long originalRecipientId = original.getDestinationUserId();
            BigDecimal reduction = Objects.equals(originalRecipientId, candidate.getDestinationUserId())
                    ? original.getAmount().subtract(candidate.getAmount())
                    : original.getAmount();

            if (reduction.signum() > 0) {
                if (recipientAccount == null && accounts != null) {
                    try {
                        recipientAccount = accounts.findByUserId(originalRecipientId).orElse(null);
                    } catch (ExternalServiceUnavailableException ex) {
                        return transactions.save(candidate.reject(
                                "Serviço externo de consulta de saldo e limites temporariamente indisponível"));
                    }
                }
                if (recipientAccount == null) {
                    return transactions.save(candidate.reject("Conta de saldo não encontrada"));
                }

                var recipientApproved = transactions.findApprovedByOriginUserId(originalRecipientId);
                var recipientReceived = transactions.findApprovedIncomingTransfersByUserId(originalRecipientId);
                var recipientSummary = rules.calculate(recipientAccount, recipientApproved, List.of(), recipientReceived);

                var recipientDecision = rules.evaluateTransferRecipientReversal(
                        recipientAccount, recipientSummary, original, candidate
                );
                if (!recipientDecision.approved()) {
                    return transactions.save(candidate.reject(recipientDecision.rejectionReason()));
                }
            }
        }

        return transactions.save(candidate);
    }
}
