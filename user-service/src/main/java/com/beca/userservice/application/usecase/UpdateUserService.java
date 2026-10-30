package com.beca.userservice.application.usecase;

import com.beca.userservice.application.exception.UserNotFoundException;
import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class UpdateUserService implements UpdateUserUseCase{
    private final UserRepositoryPort repository;

    public UpdateUserService(UserRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public User update(Long id, String name, String email) {
        User existingUser = repository.findById(id).orElseThrow(()->new UserNotFoundException(id));

        User updatedUser = new User(
                existingUser.getId(),
                name,
                email
        );

        return repository.save(updatedUser);
    }
}
