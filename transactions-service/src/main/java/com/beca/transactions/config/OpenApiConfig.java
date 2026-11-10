package com.beca.transactions.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BASIC_AUTH = "basicAuth";

    @Bean
    OpenAPI transactionsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Desafio Beca — API de Transações (MS2)")
                        .version("v1.0.0")
                        .description("Registro, consulta, conciliação e análise de despesas financeiras."))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Autenticação HTTP Basic usando e-mail e senha cadastrados no MS1.")))
                .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH));
    }
}
