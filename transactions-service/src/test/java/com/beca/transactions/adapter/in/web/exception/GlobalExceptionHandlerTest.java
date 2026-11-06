package com.beca.transactions.adapter.in.web.exception;

import com.beca.transactions.adapter.in.web.dto.CreateTransactionRequest;
import com.beca.transactions.application.exception.ExternalServiceUnavailableException;
import com.beca.transactions.application.exception.TransactionNotFoundException;
import com.beca.transactions.domain.exception.InvalidTransactionException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void devolve400ComMensagemEmPortuguesParaDestinoNaoPositivo() throws Exception {
        mvc.perform(post("/test/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "TRANSFER",
                                  "amount": 10.00,
                                  "description": "Transferência",
                                  "destinationUserId": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.destinationUserId")
                        .value("ID do usuário de destino deve ser maior que zero"));
    }

    @Test
    void incluiErroDeObjetoNaRespostaDeValidacao() throws Exception {
        var method = TestController.class.getDeclaredMethod("create", CreateTransactionRequest.class);
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new ObjectError("request", "Erro geral de validação"));
        var exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0), bindingResult
        );

        var response = new GlobalExceptionHandler().handleValidation(exception);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Erro geral de validação", response.getBody().details().get("_global"));
    }

    @Test
    void aceitaValorComZerosExcedentesNoFim() throws Exception {
        mvc.perform(post("/test/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "DEPOSIT", "amount": 10.120, "description": "Depósito"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void devolve400ComCampoParaValorComFracaoExcedente() throws Exception {
        mvc.perform(post("/test/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type": "DEPOSIT", "amount": 10.1230, "description": "Depósito"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amount")
                        .value("Valor deve ter até 17 dígitos inteiros e duas casas decimais"));
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
    void devolve400ComTiposAceitosParaTipoDeTransacaoInvalido() throws Exception {
        mvc.perform(post("/test/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INVALID",
                                  "amount": 10.00,
                                  "description": "Teste"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Tipo de transação inválido."))
                .andExpect(jsonPath("$.details.type")
                        .value("Valores aceitos: [DEPOSIT, WITHDRAW, TRANSFER, PURCHASE]"));
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
    void devolve415ComMotivoParaTipoDeConteudoNaoSuportado() throws Exception {
        mvc.perform(post("/test/transactions")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("teste"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("text/plain")));
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
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("POST")))
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
