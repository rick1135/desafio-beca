package com.beca.userservice.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    boolean existsByEmail(String email);
}
