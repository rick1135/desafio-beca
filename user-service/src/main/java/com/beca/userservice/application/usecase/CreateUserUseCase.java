package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;

public interface CreateUserUseCase {
    User create(String name, String email);
}
