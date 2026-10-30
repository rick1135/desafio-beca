package com.beca.userservice.adapter.out.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    protected UserEntity() {}

    public UserEntity(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public UserEntity(Long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
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
}
