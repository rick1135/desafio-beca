package com.beca.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String INTERNAL_AUTH_SCHEME = "InternalAuthKey";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Desafio Beca — API de Usuários (MS1)")
                        .version("v1.0.0")
                        .description("Documentação dos endpoints de cadastro, busca, atualização, remoção, "
                                + "importação em lote via CSV e autenticação interna do microsserviço de usuários."))
                .components(new Components()
                        .addSecuritySchemes(INTERNAL_AUTH_SCHEME, new SecurityScheme()
                                .name("X-Internal-Auth-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("Chave para autenticação de requisições internas entre microsserviços")));
    }
}
