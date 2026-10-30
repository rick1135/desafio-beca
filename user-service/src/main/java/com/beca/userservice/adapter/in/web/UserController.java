package com.beca.userservice.adapter.in.web;

import com.beca.userservice.adapter.in.web.dto.CreateUserRequest;
import com.beca.userservice.adapter.in.web.dto.UserResponse;
import com.beca.userservice.application.usecase.CreateUserUseCase;
import com.beca.userservice.domain.model.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/users")
public class UserController {
    private final CreateUserUseCase createUserUseCase;

    public UserController(CreateUserUseCase createUserUseCase) {
        this.createUserUseCase = createUserUseCase;
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
}
