package com.beca.transactions.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    @DisplayName("deve configurar metadados da API e o SecurityScheme para autenticação HTTP Basic")
    void shouldConfigureOpenApiMetadataAndSecurityScheme() {
        // given
        OpenApiConfig config = new OpenApiConfig();

        // when
        OpenAPI openAPI = config.transactionsOpenApi();

        // then
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Desafio Beca — API de Transações (MS2)");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("v1.0.0");
        assertThat(openAPI.getInfo().getDescription()).contains("Registro, consulta, conciliação e análise");

        assertThat(openAPI.getComponents()).isNotNull();
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey(OpenApiConfig.BASIC_AUTH);

        SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BASIC_AUTH);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("basic");

        assertThat(openAPI.getSecurity()).isNotEmpty();
        assertThat(openAPI.getSecurity().getFirst().containsKey(OpenApiConfig.BASIC_AUTH)).isTrue();
    }
}
