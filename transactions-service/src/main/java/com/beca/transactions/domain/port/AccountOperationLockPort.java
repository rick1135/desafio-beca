package com.beca.transactions.domain.port;

public interface AccountOperationLockPort {
    void lockForUser(Long userId);
}
