package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;

public interface UpdateUserUseCase {
    User update(Long id, String name, String email);
}
