package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import com.beca.userservice.application.exception.DuplicateEmailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class CreateUserServiceTest {
    @Mock
    private UserRepositoryPort repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CreateUserService service;

    @Test
    @DisplayName("deve salvar novo usuário e retornar id do usuário salvo")
    void testCreate_WhenDataIsProvided_ShouldReturnSavedUser() {
        //given
        User savedUser = new User(1L, "Ana", "12345678909", "ana@example.com", "encoded-password");
        given(passwordEncoder.encode("password")).willReturn("encoded-password");
        given(repository.save(any(User.class))).willReturn(savedUser);
        //when
        User res = service.create("Ana", "12345678909", "ana@example.com", "password");
        //then
        then(passwordEncoder).should().encode("password");
        then(repository).should().save(argThat(user ->
                "12345678909".equals(user.getCpf())
                        && "encoded-password".equals(user.getPasswordHash())
        ));

        assertEquals("Ana",  res.getName());
        assertEquals("ana@example.com", res.getEmail());
        assertEquals("12345678909", res.getCpf());
        assertEquals(1L,  res.getId());
    }

    @Test
    @DisplayName("deve normalizar email com trim e toLowerCase antes de verificar e salvar")
    void testCreate_WhenEmailHasUppercaseAndSpaces_ShouldNormalizeEmail() {
        // given
        String rawEmail = "  Ana.Teste@EXAMPLE.com  ";
        String expectedEmail = "ana.teste@example.com";
        User savedUser = new User(1L, "Ana", "12345678909", expectedEmail, "encoded-password");

        given(passwordEncoder.encode("password")).willReturn("encoded-password");
        given(repository.existsByEmail(expectedEmail)).willReturn(false);
        given(repository.save(any(User.class))).willReturn(savedUser);

        // when
        User res = service.create("Ana", "12345678909", rawEmail, "password");

        // then
        then(repository).should().existsByEmail(expectedEmail);
        then(repository).should().save(argThat(user -> expectedEmail.equals(user.getEmail())));
        assertEquals(expectedEmail, res.getEmail());
    }

    @Test
    @DisplayName("deve lançar exceção se email normalizado já existir")
    void testCreate_WhenNormalizedEmailAlreadyExists_ShouldThrowDuplicateEmailException() {
        // given
        String rawEmail = "  Ana@Example.com ";
        given(repository.existsByEmail("ana@example.com")).willReturn(true);

        // when & then
        assertThrows(DuplicateEmailException.class, () ->
                service.create("Ana", "12345678909", rawEmail, "password")
        );
        then(repository).should().existsByEmail("ana@example.com");
        then(repository).shouldHaveNoMoreInteractions();
        then(repository).shouldHaveNoMoreInteractions();
    }
}
