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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class UpdateUserServiceTest {
    @Mock
    private UserRepositoryPort repository;

    @InjectMocks
    private UpdateUserService service;

    @Test
    @DisplayName("deve atualizar os dados mantendo o ID do usuário")
    void testUpdate_WhenUserExists_ShouldReturnUpdatedUser() {
        //given
        User oldUser = new User(1L, "Ana", "12345678909", "ana@example.com", "encoded-password");
        User newUser = new User(1L, "Ana Souza", "12345678909", "ana.souza@example.com", "encoded-password");
        given(repository.findById(1L)).willReturn(Optional.of(oldUser));
        given(repository.save(any(User.class))).willReturn(newUser);
        //when
        User res = service.update(1L, "Ana Souza", "ana.souza@example.com");
        //then
        then(repository).should().findById(1L);
        then(repository).should().save(argThat(user ->
                Long.valueOf(1L).equals(user.getId())
                        && "Ana Souza".equals(user.getName())
                        && "ana.souza@example.com".equals(user.getEmail())
                        && "12345678909".equals(user.getCpf())
                        && "encoded-password".equals(user.getPasswordHash())
        ));
        assertEquals(1L, res.getId());
        assertEquals("Ana Souza", res.getName());
        assertEquals("ana.souza@example.com",  res.getEmail());
    }

    @Test
    @DisplayName("deve lançar exceção e não salvar quando usuário não existir")
    void testUpdate_WhenUserDoesNotExist_ShouldThrowException() {
        //given
        given(repository.findById(99L)).willReturn(Optional.empty());
        //when then
        assertThrows(UserNotFoundException.class, ()->service.update(99L, "Ana Souza", "ana.souza@example.com"));
        then(repository).should().findById(99L);
        then(repository).should(never()).save(any(User.class));
    }
}
