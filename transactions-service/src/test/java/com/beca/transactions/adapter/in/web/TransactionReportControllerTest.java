package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.exception.GlobalExceptionHandler;
import com.beca.transactions.application.usecase.TransactionReportService;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionReportController.class)
@Import({SecurityConfig.class, Ms1AuthenticationProvider.class,
        ApiAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class TransactionReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransactionReportService reportService;

    @MockitoBean
    private UserAuthenticationPort authPort;

    @Test
    @DisplayName("deve baixar relatório CSV com sucesso para o usuário autenticado com headers corretos")
    void shouldDownloadCsvReportSuccessfullyForAuthenticatedUser() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));

        byte[] fakeCsv = "\uFEFFdata;tipo;categoria;descricao;valor_brl;status;motivo_rejeicao;valor_usd;valor_eur\n"
                .getBytes(StandardCharsets.UTF_8);

        given(reportService.generateCsvReport(1L, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30")))
                .willReturn(fakeCsv);

        // when / then
        mvc.perform(get("/reports/transactions/csv")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .param("dataInicio", "2026-11-01")
                        .param("dataFim", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transacoes.csv\""))
                .andExpect(content().bytes(fakeCsv));

        then(reportService).should().generateCsvReport(1L, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));
    }

    @Test
    @DisplayName("deve baixar relatório CSV com datas omitidas repassando null ao serviço")
    void shouldDownloadCsvWithDefaultDatesWhenDatesOmitted() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));

        byte[] fakeCsv = "\uFEFFdata;tipo;categoria;descricao;valor_brl;status;motivo_rejeicao;valor_usd;valor_eur\n"
                .getBytes(StandardCharsets.UTF_8);

        given(reportService.generateCsvReport(1L, null, null))
                .willReturn(fakeCsv);

        // when / then
        mvc.perform(get("/reports/transactions/csv")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transacoes.csv\""))
                .andExpect(content().bytes(fakeCsv));

        then(reportService).should().generateCsvReport(1L, null, null);
    }

    @Test
    @DisplayName("deve retornar 400 quando apenas uma das datas for informada")
    void shouldReturnBadRequestWhenOnlyOneDateProvided() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));

        given(reportService.generateCsvReport(1L, LocalDate.parse("2026-11-01"), null))
                .willThrow(new InvalidTransactionException("Para filtrar por período, informe a data inicial e a data final."));

        // when / then
        mvc.perform(get("/reports/transactions/csv")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .param("dataInicio", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Para filtrar por período, informe a data inicial e a data final."));
    }

    @Test
    @DisplayName("deve retornar 400 quando o período for superior a 1 ano")
    void shouldReturnBadRequestWhenPeriodExceedsOneYear() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));

        given(reportService.generateCsvReport(1L, LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-02")))
                .willThrow(new InvalidTransactionException("O período do relatório não pode ser superior a 1 ano."));

        // when / then
        mvc.perform(get("/reports/transactions/csv")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .param("dataInicio", "2025-01-01")
                        .param("dataFim", "2026-01-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("O período do relatório não pode ser superior a 1 ano."));
    }

    @Test
    @DisplayName("deve retornar 400 quando a data inicial for posterior à data final")
    void shouldReturnBadRequestWhenStartDateIsAfterEndDate() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));

        given(reportService.generateCsvReport(1L, LocalDate.parse("2026-11-30"), LocalDate.parse("2026-11-01")))
                .willThrow(new InvalidTransactionException("A data inicial deve ser anterior ou igual à data final."));

        // when / then
        mvc.perform(get("/reports/transactions/csv")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .param("dataInicio", "2026-11-30")
                        .param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("A data inicial deve ser anterior ou igual à data final."));
    }

    @Test
    @DisplayName("deve retornar 400 quando o formato da data for inválido")
    void shouldReturnBadRequestWhenDateFormatIsInvalid() throws Exception {
        // given
        given(authPort.authenticate("ana@example.com", "Senha123"))
                .willReturn(Optional.of(new AuthenticatedUser(1L, "Ana", "ana@example.com")));

        // when / then
        mvc.perform(get("/reports/transactions/csv")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth("ana@example.com", "Senha123"))
                        .param("dataInicio", "2026-13-45"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }

    @Test
    @DisplayName("deve retornar 401 para requisição sem autenticação")
    void shouldReturnUnauthorizedWhenNoAuthenticationProvided() throws Exception {
        // when / then
        mvc.perform(get("/reports/transactions/csv"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        verifyNoInteractions(authPort, reportService);
    }

    private static String basicAuth(String email, String password) {
        String token = Base64.getEncoder().encodeToString(
                (email + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
