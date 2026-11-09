package com.beca.userservice.application.usecase;

import com.beca.userservice.config.RepositoryUserDetailsService.AuthenticatedUserPrincipal;
import com.beca.userservice.domain.model.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthenticateUserService {
    private final AuthenticationManager authenticationManager;

    public AuthenticateUserService(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public User authenticate(String email, String senha) {
        var request = UsernamePasswordAuthenticationToken.unauthenticated(email, senha);
        var authentication = authenticationManager.authenticate(request);

        if (authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
            return principal.user();
        }
        throw new BadCredentialsException("Credenciais inválidas");
    }
}
