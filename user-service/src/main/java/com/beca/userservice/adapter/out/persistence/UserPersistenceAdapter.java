package com.beca.userservice.adapter.out.persistence;

import com.beca.userservice.application.exception.DuplicateEmailException;
import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
        try{
            UserEntity saved = userJpaRepository.saveAndFlush(toEntity(user));
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex){
            Throwable cause = ex;
            while (cause!=null){
                if(cause instanceof ConstraintViolationException violation && "uk_users_email".equals(violation.getConstraintName())){
                    throw new DuplicateEmailException();
                }
                cause = cause.getCause();
            }
            throw  ex;
        }
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
        return new User(entity.getId(), entity.getName(), entity.getCpf(), entity.getEmail(), entity.getPasswordHash());
    }

    private UserEntity toEntity(User user){
        return new UserEntity(user.getId(), user.getName(), user.getCpf(), user.getEmail(), user.getPasswordHash());
    }
}
