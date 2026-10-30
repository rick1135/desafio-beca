package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;

public interface GetUserByIdUseCase {
    User getUserById(Long id);
}
