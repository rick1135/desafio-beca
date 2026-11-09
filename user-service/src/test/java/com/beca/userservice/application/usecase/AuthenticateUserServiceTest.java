package com.beca.userservice.application.usecase;

import com.beca.userservice.config.RepositoryUserDetailsService;
import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {
    @Mock
    private UserRepositoryPort users;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthenticateUserService service;

    @BeforeEach
    void setUp() {
        var provider = new DaoAuthenticationProvider(new RepositoryUserDetailsService(users));
        provider.setPasswordEncoder(passwordEncoder);
        service = new AuthenticateUserService(new ProviderManager(provider));
    }

    @Test
    @DisplayName("deve autenticar com senha correta e email normalizado")
    void authenticatesWithCorrectPasswordAndNormalizedEmail() {
        // given
        String email = "ana@example.com";
        User user = new User(15L, "Ana", "12345678909", email,
                passwordEncoder.encode("Senha123"));
        given(users.findByEmail(email)).willReturn(Optional.of(user));

        // when
        User authenticated = service.authenticate("  ANA@EXAMPLE.COM  ", "Senha123");

        // then
        assertEquals(15L, authenticated.getId());
        assertEquals(email, authenticated.getEmail());
        then(users).should().findByEmail(email);
    }

    @Test
    @DisplayName("deve rejeitar senha incorreta")
    void rejectsIncorrectPassword() {
        // given
        String email = "ana@example.com";
        User user = new User(15L, "Ana", "12345678909", email,
                passwordEncoder.encode("Senha123"));
        given(users.findByEmail(email)).willReturn(Optional.of(user));

        // when
        assertThrows(BadCredentialsException.class,
                () -> service.authenticate(email, "senha-errada"));

        // then
        then(users).should().findByEmail(email);
    }

    @Test
    @DisplayName("deve rejeitar email inexistente")
    void rejectsUnknownEmail() {
        // given
        String email = "desconhecido@example.com";
        given(users.findByEmail(email)).willReturn(Optional.empty());

        // when
        assertThrows(BadCredentialsException.class,
                () -> service.authenticate(email, "Senha123"));

        // then
        then(users).should().findByEmail(email);
    }
}
