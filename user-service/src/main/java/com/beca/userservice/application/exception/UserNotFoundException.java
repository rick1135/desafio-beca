package com.beca.userservice.application.exception;

public class UserNotFoundException extends RuntimeException{
    public UserNotFoundException(Long id) {
        super("Usuário não encontrado com id " + id + "!");
    }
}
