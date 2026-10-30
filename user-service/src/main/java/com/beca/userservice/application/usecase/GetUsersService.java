package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetUsersService implements GetUsersUseCase {
    private final UserRepositoryPort repository;

    public GetUsersService(UserRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public List<User> getUsers() {
        return repository.findAll();
    }
}
