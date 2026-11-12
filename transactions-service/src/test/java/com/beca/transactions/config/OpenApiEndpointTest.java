package com.beca.transactions.config;

import com.beca.transactions.application.usecase.CreateTransactionService;
import com.beca.transactions.application.usecase.GetBalanceService;
import com.beca.transactions.application.usecase.GetExpenseSeriesService;
import com.beca.transactions.application.usecase.GetExpensesByCategoryService;
import com.beca.transactions.application.usecase.GetTransactionService;
import com.beca.transactions.application.usecase.TransactionReportService;
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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
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
    private Ms1AuthenticationProvider authenticationProvider;

    @MockitoBean
    private ApiAuthenticationEntryPoint authenticationEntryPoint;

    @MockitoBean
    private CreateTransactionService createTransactionService;

    @MockitoBean
    private GetTransactionService getTransactionService;

    @MockitoBean
    private GetBalanceService getBalanceService;

    @MockitoBean
    private GetExpensesByCategoryService getExpensesByCategoryService;

    @MockitoBean
    private GetExpenseSeriesService getExpenseSeriesService;

    @MockitoBean
    private TransactionReportService transactionReportService;

    @Test
    @DisplayName("deve expor a documentação OpenAPI em /v3/api-docs sem exigir autenticação")
    void exposesOpenApiDocumentationPublicly() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Desafio Beca — API de Transações (MS2)"))
                .andExpect(jsonPath("$.info.version").value("v1.0.0"))
                .andExpect(jsonPath("$.paths['/transactions']").exists())
                .andExpect(jsonPath("$.paths['/balance']").exists())
                .andExpect(jsonPath("$.paths['/expenses/by-category']").exists())
                .andExpect(jsonPath("$.paths['/expenses/daily']").exists())
                .andExpect(jsonPath("$.paths['/expenses/monthly']").exists())
                .andExpect(jsonPath("$.paths['/reports/transactions/csv']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth").exists())
                .andExpect(jsonPath("$.components.schemas.CreateTransactionRequest.properties.type.example")
                        .value("PURCHASE"))
                .andExpect(jsonPath("$.components.schemas.TransactionResponse.properties.type.example")
                        .value("PURCHASE"))
                .andExpect(jsonPath("$.paths['/transactions'].get.parameters[?(@.name == 'type')].example")
                        .value(contains("PURCHASE")))
                .andExpect(jsonPath("$.paths['/transactions'].post.requestBody.content['application/json']"
                        + ".examples.Compra.value.type").value("PURCHASE"))
                .andExpect(jsonPath("$.paths['/transactions'].post.requestBody.required").value(true))
                .andExpect(jsonPath("$.paths['/transactions'].post.responses['201'].description")
                        .value(containsString("REJECTED")))
                .andExpect(jsonPath("$.paths['/transactions'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/transactions'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/transactions'].get.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/balance'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths.*.*.responses['503'].description",
                        hasSize(9)))
                .andExpect(jsonPath("$.components.schemas.ExpensePointResponse.properties.period.example")
                        .doesNotExist());
    }
}
