package com.beca.transactions.adapter.in.web.exception;

import com.beca.transactions.adapter.in.web.dto.CreateTransactionRequest;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void devolve400ComCampoInvalido() throws Exception {
        mvc.perform(post("/test/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PURCHASE",
                                  "amount": 10.00,
                                  "description": "Compra"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.category")
                        .value("Categoria é obrigatório para compras"));
    }

    @Test
    void devolve400ParaJsonInvalido() throws Exception {
        mvc.perform(post("/test/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Corpo da requisição inválido ou malformado."));
    }

    @Test
    void devolve404ParaTransacaoInexistente() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Transação 42 não encontrada"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void devolve503ParaServicoExternoIndisponivel() throws Exception {
        mvc.perform(get("/test/external-failure"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message")
                        .value("Serviço externo indisponível no momento."))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void devolve500SemExporDetalhesInternos() throws Exception {
        mvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message")
                        .value("Erro interno ao processar a requisição."))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("segredo"))));
    }

    @Test
    void devolve400ParaRegraDeNegocioInvalida() throws Exception {
        mvc.perform(get("/test/invalid-transaction"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Não é possível realizar transferência para si mesmo"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void devolve400ParaParametroDeRotaInvalido() throws Exception {
        mvc.perform(get("/test/by-id/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.details.id")
                        .value("Valor incompatível com o tipo esperado"));
    }

    @Test
    void devolve405ParaMetodoHttpNaoPermitido() throws Exception {
        mvc.perform(post("/test/by-id/123"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(header().string("Allow",
                        org.hamcrest.Matchers.containsString("GET")));
    }

    @RestController
    static class TestController {
        @PostMapping("/test/transactions")
        void create(@Valid @RequestBody CreateTransactionRequest request) {
        }

        @GetMapping("/test/not-found")
        void notFound() {
            throw new TransactionNotFoundException(42L);
        }

        @GetMapping("/test/external-failure")
        void externalFailure() {
            throw new ExternalServiceUnavailableException(
                    "MockAPI", new RuntimeException("Falha de conexão")
            );
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("segredo interno");
        }

        @GetMapping("/test/invalid-transaction")
        void invalidTransaction() {
            throw new InvalidTransactionException(
                    "Não é possível realizar transferência para si mesmo"
            );
        }

        @GetMapping("/test/by-id/{id}")
        void byId(@PathVariable Long id) {
        }
    }
}
