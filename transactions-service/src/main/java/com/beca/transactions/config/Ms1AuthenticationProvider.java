package com.beca.transactions.config;

import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.domain.port.UserAuthenticationPort;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class Ms1AuthenticationProvider implements AuthenticationProvider {
    private final UserAuthenticationPort userAuthenticationPort;

    public Ms1AuthenticationProvider(UserAuthenticationPort userAuthenticationPort) {
        this.userAuthenticationPort = userAuthenticationPort;
    }

    @Override
    public Authentication authenticate(Authentication authentication){
        String email=authentication.getName();
        String senha = (String) authentication.getCredentials();

        if(email==null || email.isBlank() || senha==null || senha.isBlank()){
            throw new BadCredentialsException("Credenciais inválidas");
        }

        try{
            var user = userAuthenticationPort.authenticate(email, senha)
                    .orElseThrow(()-> new BadCredentialsException("Credenciais inválidas"));

            return new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
        } catch (ExternalServiceUnavailableException ex){
            throw new AuthenticationServiceException("Serviço de autenticação indisponível", ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication){
        return UsernamePasswordAuthenticationToken.class
                .isAssignableFrom(authentication);
    }
}
