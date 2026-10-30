package com.beca.userservice.application.usecase;

import com.beca.userservice.application.exception.UserNotFoundException;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class DeleteUserService implements DeleteUserUseCase{
    private final UserRepositoryPort repository;

    public DeleteUserService(UserRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public void delete(Long id){
        repository.findById(id).orElseThrow(()->new UserNotFoundException(id));
        repository.deleteById(id);
    }
}
