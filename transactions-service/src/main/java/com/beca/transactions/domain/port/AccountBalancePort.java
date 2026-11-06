package com.beca.transactions.domain.port;

import com.beca.transactions.domain.model.AccountBalance;

import java.util.Optional;

public interface AccountBalancePort {
    Optional<AccountBalance> findByUserId(Long userId);
}
