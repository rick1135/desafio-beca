package com.beca.transactions.domain.port;

import com.beca.transactions.domain.model.AuthenticatedUser;

import java.util.Optional;

public interface UserAuthenticationPort {
    Optional<AuthenticatedUser> authenticate(String email, String senha);
}
