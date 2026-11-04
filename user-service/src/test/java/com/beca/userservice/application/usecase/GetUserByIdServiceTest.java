package com.beca.userservice.application.usecase;

import com.beca.userservice.application.exception.UserNotFoundException;
import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class GetUserByIdServiceTest {
    @Mock
    private UserRepositoryPort repository;

    @InjectMocks
    private GetUserByIdService service;

    @Test
    @DisplayName("deve retornar o usuário quando ID existir")
    void testGetUserById_WhenUserExist_ShouldReturnUser() {
        //given
        User user = new User(1L, "Ana", "12345678909", "ana@example.com", "encoded-password");
        given(repository.findById(1L)).willReturn(Optional.of(user));
        //when
        User res =  service.getUserById(1L);
        //then
        assertEquals(user.getId(), res.getId());
        assertEquals(user.getName(), res.getName());
        assertEquals(user.getEmail(), res.getEmail());
        then(repository).should().findById(1L);
    }

    @Test
    @DisplayName("deve lançar exceção quando ID não existir")
    void testGetUserById_WhenUserDoesNotExist_ShouldThrowException() {
        //given
        given(repository.findById(99L)).willReturn(Optional.empty());
        //when e then
        assertThrows(UserNotFoundException.class, () -> service.getUserById(99L));
        then(repository).should().findById(99L);
    }
}