package com.beca.userservice.adapter.in.web;

import com.beca.userservice.adapter.in.web.dto.CreateUserRequest;
import com.beca.userservice.adapter.in.web.dto.UpdateUserRequest;
import com.beca.userservice.adapter.in.web.dto.UserResponse;
import com.beca.userservice.application.usecase.*;
import com.beca.userservice.domain.model.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final CreateUserUseCase createUserUseCase;
    private final GetUsersUseCase getUsersUseCase;
    private final GetUserByIdUseCase getUserByIdUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase, GetUsersUseCase getUsersUseCase, GetUserByIdUseCase getUserByIdUseCase, UpdateUserUseCase updateUserUseCase, DeleteUserUseCase deleteUserUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUsersUseCase = getUsersUseCase;
        this.getUserByIdUseCase = getUserByIdUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = createUserUseCase.create(
                request.name(),
                request.email()
        );

        UserResponse response = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );

        URI location = URI.create("/users/" + user.getId());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        List<UserResponse> users = getUsersUseCase.getUsers()
                .stream()
                .map(user -> toResponse(user))
                .toList();

        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        User user = getUserByIdUseCase.getUserById(id);
        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User user = updateUserUseCase.update(
                id,
                request.name(),
                request.email()
        );

        return ResponseEntity.ok(toResponse(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserById(@PathVariable Long id) {
        deleteUserUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
