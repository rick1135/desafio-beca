package com.beca.userservice.application.usecase;

import com.beca.userservice.domain.model.User;

import java.util.List;

public interface GetUsersUseCase {
    List<User> getUsers();
}
