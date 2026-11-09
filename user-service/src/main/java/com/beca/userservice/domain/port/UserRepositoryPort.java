package com.beca.userservice.domain.port;

import com.beca.userservice.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    List<User> findAll();

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    void deleteById(Long id);
}
