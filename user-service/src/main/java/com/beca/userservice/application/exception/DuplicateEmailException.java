package com.beca.userservice.application.exception;

public class DuplicateEmailException extends RuntimeException{
    public DuplicateEmailException(){
        super("Email já cadastrado");
    }
}
