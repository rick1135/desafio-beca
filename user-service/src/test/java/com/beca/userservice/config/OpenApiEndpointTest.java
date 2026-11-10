package com.beca.userservice.config;

import com.beca.userservice.application.usecase.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = "INTERNAL_AUTH_KEY=chave-de-teste")
@Import({OpenApiConfig.class, SecurityConfig.class})
@EnableWebSecurity
@ImportAutoConfiguration({
        SpringDocConfigProperties.class,
        SpringDocConfiguration.class,
        SpringDocWebMvcConfiguration.class
})
class OpenApiEndpointTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthenticateUserService authenticateUserService;

    @MockitoBean
    private CreateUserUseCase createUserUseCase;

    @MockitoBean
    private GetUsersUseCase getUsersUseCase;

    @MockitoBean
    private GetUserByIdUseCase getUserByIdUseCase;

    @MockitoBean
    private UpdateUserUseCase updateUserUseCase;

    @MockitoBean
    private DeleteUserUseCase deleteUserUseCase;

    @MockitoBean
    private ImportUsersUseCase importUsersUseCase;

    @Test
    @DisplayName("deve expor a documentação OpenAPI em /v3/api-docs com sucesso")
    void exposesOpenApiDocumentation() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Desafio Beca — API de Usuários (MS1)"))
                .andExpect(jsonPath("$.info.version").value("v1.0.0"))
                .andExpect(jsonPath("$.paths['/users']").exists())
                .andExpect(jsonPath("$.paths['/internal/auth/validate']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.InternalAuthKey").exists());
    }
}

