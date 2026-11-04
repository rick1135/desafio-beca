package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
}
