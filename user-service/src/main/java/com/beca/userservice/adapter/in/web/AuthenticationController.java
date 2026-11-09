package com.beca.userservice.adapter.in.web;

import com.beca.userservice.adapter.in.web.dto.AuthenticatedUserResponse;
import com.beca.userservice.adapter.in.web.dto.ValidateCredentialsRequest;
import com.beca.userservice.application.usecase.AuthenticateUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
public class AuthenticationController {
    private final AuthenticateUserService authenticateUserService;

    public AuthenticationController(AuthenticateUserService authenticateUserService) {
        this.authenticateUserService = authenticateUserService;
    }

    @PostMapping("/validate")
    public ResponseEntity<AuthenticatedUserResponse> validate(@Valid @RequestBody ValidateCredentialsRequest request) {
        var user = authenticateUserService.authenticate(request.email(), request.senha());
        return ResponseEntity.ok(new AuthenticatedUserResponse(user.getId(), user.getName(), user.getEmail()));
    }
}
