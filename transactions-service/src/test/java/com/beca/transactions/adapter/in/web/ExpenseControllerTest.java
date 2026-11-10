package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.usecase.GetExpensesByCategoryService;
import com.beca.transactions.application.usecase.GetExpensesByCategoryService.CategoryExpense;
import com.beca.transactions.application.usecase.GetExpensesByCategoryService.ExpenseSummary;
import com.beca.transactions.config.ApiAuthenticationEntryPoint;
import com.beca.transactions.config.Ms1AuthenticationProvider;
import com.beca.transactions.config.SecurityConfig;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import com.beca.transactions.domain.model.AuthenticatedUser;
import com.beca.transactions.domain.port.UserAuthenticationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@Import({SecurityConfig.class, Ms1AuthenticationProvider.class,
        ApiAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class ExpenseControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetExpensesByCategoryService expenses;

    @MockitoBean
    private UserAuthenticationPort authPort;

    @Test
    @DisplayName("deve retornar resumo de despesas por categoria para o usuário autenticado")
    void returnsExpensesByCategoryForAuthenticatedUser() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));
        given(expenses.getExpenses(1L, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")))
                .willReturn(new ExpenseSummary(
                        "BRL",
                        new BigDecimal("150.00"),
                        List.of(
                                new CategoryExpense("Alimentação", new BigDecimal("100.00"), new BigDecimal("66.67")),
                                new CategoryExpense("Retirada", new BigDecimal("50.00"), new BigDecimal("33.33"))
                        )
                ));

        // when / then
        mvc.perform(get("/expenses/by-category")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .param("startDate", "2026-11-01")
                        .param("endDate", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.total").value(150.00))
                .andExpect(jsonPath("$.categories[0].category").value("Alimentação"))
                .andExpect(jsonPath("$.categories[0].total").value(100.00))
                .andExpect(jsonPath("$.categories[0].percentage").value(66.67))
                .andExpect(jsonPath("$.categories[1].category").value("Retirada"))
                .andExpect(jsonPath("$.categories[1].total").value(50.00))
                .andExpect(jsonPath("$.categories[1].percentage").value(33.33));

        then(expenses).should().getExpenses(1L, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));
    }

    @Test
    @DisplayName("deve retornar 400 quando o período informado for inválido")
    void returnsBadRequestWhenDateRangeIsInvalid() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));
        given(expenses.getExpenses(1L, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-01")))
                .willThrow(new InvalidTransactionException("A data inicial deve ser anterior ou igual à data final."));

        // when / then
        mvc.perform(get("/expenses/by-category")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .param("startDate", "2026-11-20")
                        .param("endDate", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("A data inicial deve ser anterior ou igual à data final."));
    }

    @Test
    @DisplayName("deve retornar 401 ao consultar despesas por categoria sem credenciais")
    void rejectsUnauthenticatedRequest() throws Exception {
        // when / then
        mvc.perform(get("/expenses/by-category"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(authPort, expenses);
    }

    private static String basicAuth(String email, String password) {
        String token = Base64.getEncoder().encodeToString(
                (email + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
