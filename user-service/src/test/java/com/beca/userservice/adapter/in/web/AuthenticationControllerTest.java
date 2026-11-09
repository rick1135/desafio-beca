package com.beca.userservice.adapter.in.web;

import com.beca.userservice.application.usecase.AuthenticateUserService;
import com.beca.userservice.config.SecurityConfig;
import com.beca.userservice.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthenticationController.class, properties = "INTERNAL_AUTH_KEY=chave-de-teste")
@Import(SecurityConfig.class)
@EnableWebSecurity
class AuthenticationControllerTest {
    private static final String KEY_HEADER = "X-Internal-Auth-Key";
    private static final String VALID_KEY = "chave-de-teste";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticateUserService authenticateUserService;

    @Test
    @DisplayName("deve retornar identidade sem senha quando credenciais forem válidas")
    void returnsIdentityWithoutPassword() throws Exception {
        // given
        String email = "ana@example.com";
        given(authenticateUserService.authenticate(email, "Senha123"))
                .willReturn(new User(15L, "Ana", "12345678909", email, "hash-secreto"));

        // when / then
        mvc.perform(post("/internal/auth/validate")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","senha":"Senha123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.nome").value("Ana"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        then(authenticateUserService).should().authenticate(email, "Senha123");
    }

    @Test
    @DisplayName("deve retornar 401 com mensagem padronizada para senha incorreta")
    void returnsUnauthorizedForInvalidCredentials() throws Exception {
        // given
        String email = "ana@example.com";
        given(authenticateUserService.authenticate(email, "senha-errada"))
                .willThrow(new BadCredentialsException("Credenciais inválidas"));

        // when / then
        mvc.perform(post("/internal/auth/validate")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","senha":"senha-errada"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));

        then(authenticateUserService).should().authenticate(email, "senha-errada");
    }

    @Test
    @DisplayName("deve retornar 503 quando o serviço de autenticação estiver indisponível")
    void returnsServiceUnavailableForAuthenticationFailure() throws Exception {
        // given
        given(authenticateUserService.authenticate("ana@example.com", "Senha123"))
                .willThrow(new InternalAuthenticationServiceException("Banco indisponível"));

        // when / then
        mvc.perform(post("/internal/auth/validate")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ana@example.com","senha":"Senha123"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Serviço de autenticação indisponível no momento."));
    }

    @Test
    @DisplayName("deve retornar 403 sem chave interna e sem consultar a autenticação")
    void rejectsMissingInternalKey() throws Exception {
        // given
        String request = """
                {"email":"ana@example.com","senha":"Senha123"}
                """;

        // when / then
        mvc.perform(post("/internal/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authenticateUserService);
    }

    @Test
    @DisplayName("deve retornar 403 com chave interna inválida e sem consultar a autenticação")
    void rejectsInvalidInternalKey() throws Exception {
        // given
        String request = """
                {"email":"ana@example.com","senha":"Senha123"}
                """;

        // when / then
        mvc.perform(post("/internal/auth/validate")
                        .header(KEY_HEADER, "chave-incorreta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authenticateUserService);
    }

    @Test
    @DisplayName("deve retornar 400 e identificar senha ausente")
    void returnsBadRequestForMissingPassword() throws Exception {
        // given
        String request = """
                {"email":"ana@example.com"}
                """;

        // when / then
        mvc.perform(post("/internal/auth/validate")
                        .header(KEY_HEADER, VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.senha").exists());

        verifyNoInteractions(authenticateUserService);
    }
}
