package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class CreateUserServiceTest {
    @Mock
    private UserRepositoryPort repository;

    @InjectMocks
    private CreateUserService service;

    @Test
    @DisplayName("deve salvar novo usuário e retornar id do usuário salvo")
    void testCreate_WhenDataIsProvided_ShouldReturnSavedUser() {
        //given
        User savedUser = new User(1L, "Ana", "ana@example.com");
        given(repository.save(any(User.class))).willReturn(savedUser);
        //when
        User res = service.create("Ana", "ana@example.com");
        //then
        then(repository).should().save(any(User.class));

        assertEquals("Ana",  res.getName());
        assertEquals("ana@example.com",  res.getEmail());
        assertEquals(1L,  res.getId());
    }
}
