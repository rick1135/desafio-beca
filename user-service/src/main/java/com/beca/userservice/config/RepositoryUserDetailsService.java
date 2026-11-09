package com.beca.userservice.config;

import com.beca.userservice.domain.model.User;
import com.beca.userservice.domain.port.UserRepositoryPort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
public class RepositoryUserDetailsService implements UserDetailsService {
    private final UserRepositoryPort users;

    public RepositoryUserDetailsService(UserRepositoryPort users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        return users.findByEmail(normalizedEmail)
                .map(AuthenticatedUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }

    public record AuthenticatedUserPrincipal(User user) implements UserDetails {
        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));
        }

        @Override
        public String getPassword() {
            return user.getPasswordHash();
        }

        @Override
        public String getUsername() {
            return user.getEmail();
        }
    }
}
