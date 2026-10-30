package com.beca.userservice.adapter.out.persistence;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserPersistenceAdapter implements UserRepositoryPort {
    private final UserJpaRepository userJpaRepository;

    public UserPersistenceAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = toEntity(user);
        UserEntity savedEntity = userJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id).map(entity -> toDomain(entity));
    }

    @Override
    public List<User> findAll() {
        return userJpaRepository.findAll()
                .stream()
                .map(entity -> toDomain(entity))
                .toList();
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public void deleteById(Long id) {
        userJpaRepository.deleteById(id);
    }

    private User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getName(), entity.getEmail());
    }

    private UserEntity toEntity(User user){
        return new UserEntity(user.getId(), user.getName(), user.getEmail());
    }
}
