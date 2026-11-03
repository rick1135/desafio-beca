package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class GetUsersServiceTest {
    @Mock
    private UserRepositoryPort repository;

    @InjectMocks
    private GetUsersService service;

    @Test
    @DisplayName("deve retornar os usuários cadastrados")
    void testGestUsers_WhenUsersExist_ShouldReturnUsers(){
        //given
        User ana = new User(1L, "Ana", "ana@example.com");
        User joao = new User(2L, "João", "joao@example.com");
        given(repository.findAll()).willReturn(List.of(ana, joao));
        //when
        List<User> res = service.getUsers();
        //then
        assertEquals(2, res.size());
        assertEquals(ana, res.get(0));
        assertEquals(joao, res.get(1));
        then(repository).should().findAll();
    }

    @Test
    @DisplayName("deve retornar uma lista vazia quando não tiver usuários")
    void testGestUsers_WhenNoUsersExist_ShouldReturnEmptyList(){
        //given
        given(repository.findAll()).willReturn(List.of());
        //when
        List<User> res = service.getUsers();
        //then
        assertTrue(res.isEmpty());
        then(repository).should().findAll();
    }
}
