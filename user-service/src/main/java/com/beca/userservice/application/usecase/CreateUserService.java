package com.beca.userservice.application.usecase;

import com.beca.userservice.application.exception.DuplicateEmailException;
import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CreateUserService implements CreateUserUseCase{
    private final UserRepositoryPort repository;
    private final PasswordEncoder passwordEncoder;

    public CreateUserService(UserRepositoryPort repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User create(String name, String cpf, String email, String password) {
        String normalizedEmail = (email!=null) ? email.trim().toLowerCase() : null;
        if(repository.existsByEmail(normalizedEmail)) throw new DuplicateEmailException();
        String passwordHash = passwordEncoder.encode(password);
        User user = new User(null, name, cpf, normalizedEmail, passwordHash);
        return repository.save(user);
    }
}
