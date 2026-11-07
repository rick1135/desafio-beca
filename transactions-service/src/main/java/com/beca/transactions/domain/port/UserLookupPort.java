package com.beca.transactions.domain.port;

public interface UserLookupPort {
    boolean existsById(Long userId);
}
