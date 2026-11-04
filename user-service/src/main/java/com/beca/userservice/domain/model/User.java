package com.beca.userservice.domain.model;

public class User {
    private final Long id;
    private final String name;
    private final String email;
    private final String cpf;
    private final String passwordHash;

    public User(Long id, String name, String cpf, String email, String passwordHash) {
        this.id = id;
        this.name = name;
        this.cpf = cpf;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getCpf() {
        return cpf;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
