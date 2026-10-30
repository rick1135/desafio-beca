package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class CreateUserService implements CreateUserUseCase{
    private final UserRepositoryPort repository;

    public CreateUserService(UserRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public User create(String name, String email) {
        User user = new User(null, name, email);
        return repository.save(user);
    }
}
