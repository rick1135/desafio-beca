package com.beca.transactions.application.usecase;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.model.BalanceRules;
import com.beca.transactions.domain.model.Transaction;
import com.beca.transactions.domain.model.TransactionStatus;
import com.beca.transactions.domain.model.TransactionType;
import com.beca.transactions.domain.port.AccountBalancePort;
import com.beca.transactions.domain.port.TransactionRepositoryPort;
import com.beca.transactions.domain.port.AccountOperationLockPort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class CreateTransactionService {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String MOCK_UNAVAILABLE =
            "Serviço externo de consulta de saldo e limites temporariamente indisponível";

    private final TransactionRepositoryPort transactions;
    private final AccountBalancePort accounts;
    private final BalanceRules rules = new BalanceRules();
    private final AccountOperationLockPort accountLock;

    public CreateTransactionService(TransactionRepositoryPort transactions, AccountBalancePort accounts, AccountOperationLockPort accountLock) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.accountLock = accountLock;
    }

    @Transactional
    public Transaction create(Long originUserId, TransactionType type, BigDecimal amount,
            String description, String category, Long destinationUserId, Long originalTransactionId
    ){
        Instant now = Instant.now();
        TransactionStatus status = TransactionStatus.APPROVED;
        String rejectionReason = null;

        if (type!=TransactionType.DEPOSIT){
            try {
                var account = accounts.findByUserId(originUserId);
                if(account.isEmpty()){
                    status=TransactionStatus.REJECTED;
                    rejectionReason="Conta de saldo não encontrada";
                }else{
                    accountLock.lockForUser(originUserId);

                    LocalDate today = LocalDate.ofInstant(now, ZONE);
                    Instant start = today.atStartOfDay(ZONE).toInstant();
                    Instant end = today.plusDays(1).atStartOfDay(ZONE).toInstant();

                    var approved = transactions.findApprovedByOriginUserId(originUserId);
                    var todayOutflows = transactions.findApprovedOutflowsByOriginUserIdBetween(
                                    originUserId, start, end
                            );

                    var summary = rules.calculate(account.get(), approved, todayOutflows);
                    var decision = rules.evaluateOutflow(account.get(), summary, amount);

                    if (!decision.approved()) {
                        status = TransactionStatus.REJECTED;
                        rejectionReason = decision.rejectionReason();
                    }
                }
            } catch (ExternalServiceUnavailableException ex){
                status=TransactionStatus.REJECTED;
                rejectionReason=MOCK_UNAVAILABLE;
            }
        }

        if (type == TransactionType.DEPOSIT) {
            accountLock.lockForUser(originUserId);
        }

        Transaction transaction = new Transaction(
                null,
                type,
                amount,
                category,
                originUserId,
                destinationUserId,
                status,
                rejectionReason,
                originalTransactionId,
                description,
                now
        );

        return transactions.save(transaction);
    }
}
