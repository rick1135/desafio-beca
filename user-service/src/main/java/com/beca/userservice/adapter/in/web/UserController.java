package com.beca.userservice.adapter.in.web;

import com.beca.userservice.adapter.in.web.dto.CreateUserRequest;
import com.beca.userservice.adapter.in.web.dto.UserResponse;
import com.beca.userservice.application.usecase.CreateUserUseCase;
import com.beca.userservice.application.usecase.GetUserByIdUseCase;
import com.beca.userservice.application.usecase.GetUsersUseCase;
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

    public UserController(CreateUserUseCase createUserUseCase, GetUsersUseCase getUsersUseCase, GetUserByIdUseCase getUserByIdUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUsersUseCase = getUsersUseCase;
        this.getUserByIdUseCase = getUserByIdUseCase;
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

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
