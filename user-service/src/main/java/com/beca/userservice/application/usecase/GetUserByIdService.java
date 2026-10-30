package com.beca.userservice.application.usecase;

import com.beca.userservice.application.exception.UserNotFoundException;
import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class GetUserByIdService implements GetUserByIdUseCase{
    private final UserRepositoryPort repository;

    public GetUserByIdService(UserRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public User getUserById(Long id){
        return repository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
