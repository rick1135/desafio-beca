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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class DeleteUserServiceTest {
    @Mock
    private UserRepositoryPort repository;

    @InjectMocks
    private DeleteUserService service;

    @Test
    @DisplayName("deve excluir usuário quando ID existir")
    void testDelete_WhenUserExists_ShouldDeleteUser(){
        //given
        User user = new User(1L, "Ana", "ana@example.com");
        given(repository.findById(1L)).willReturn(Optional.of(user));
        //when
        assertDoesNotThrow(() -> service.delete(1L));
        //then
        then(repository).should().findById(1L);
        then(repository).should().deleteById(1L);
    }

    @Test
    @DisplayName("deve lançar exceção e não excluir quando ID não existir")
    void testDelete_WhenUserDoesNotExist_ShouldThrowException(){
        //given
        given(repository.findById(99L)).willReturn(Optional.empty());
        //when then
        assertThrows(UserNotFoundException.class, () -> service.delete(99L));
        then(repository).should().findById(99L);
        then(repository).should(never()).deleteById(anyLong());
    }
}
