package com.beca.userservice.adapter.in.web;

import com.beca.userservice.adapter.in.web.dto.UploadLineResult;
import com.beca.userservice.adapter.in.web.dto.UploadResponse;
import com.beca.userservice.application.usecase.*;
import com.beca.userservice.adapter.in.web.dto.ApiErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerUploadTest {
    @Mock
    private CreateUserUseCase createUserUseCase;

    @Mock
    private GetUsersUseCase getUsersUseCase;

    @Mock
    private GetUserByIdUseCase getUserByIdUseCase;

    @Mock
    private UpdateUserUseCase updateUserUseCase;

    @Mock
    private DeleteUserUseCase deleteUserUseCase;

    @Mock
    private ImportUsersUseCase importUsersUseCase;

    @Test
    void passesCsvRowsToUseCaseAndMapsItsReport() {
        String csv = """
                nome,cpf,email,senha
                Ana Teste,12345678909,ana@example.com,Senha123
                Bruno Teste,123456789,bruno@example.com,Senha456
                Carla Teste,98765432100,ana@example.com,Senha789
                Davi Teste,11122233344,davi@example.com,Senha321
                """;

        when(importUsersUseCase.importUsers(anyList())).thenReturn(new ImportUsersResult(
                4, 2, 2, java.util.List.of(
                        new ImportUserResult(1, "IMPORTADO", 1L, null),
                        new ImportUserResult(2, "FALHA", null, "cpf inválido"),
                        new ImportUserResult(3, "FALHA", null, "EMAIL_DUPLICADO"),
                        new ImportUserResult(4, "IMPORTADO", 2L, null)
                )
        ));

        ResponseEntity<?> response = controller().upload(csvFile(csv));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UploadResponse body = assertInstanceOf(UploadResponse.class, response.getBody());
        assertEquals(4, body.total());
        assertEquals(2, body.importados());
        assertEquals(2, body.falhas());
        assertEquals(4, body.resultados().size());
        assertEquals(new UploadLineResult(1, "IMPORTADO", 1L, null), body.resultados().get(0));
        assertEquals("FALHA", body.resultados().get(1).status());
        assertEquals(new UploadLineResult(2, "FALHA", null, "cpf inválido"), body.resultados().get(1));
        assertEquals(new UploadLineResult(3, "FALHA", null, "EMAIL_DUPLICADO"), body.resultados().get(2));
        assertEquals(new UploadLineResult(4, "IMPORTADO", 2L, null), body.resultados().get(3));
        verify(importUsersUseCase).importUsers(argThat(rows ->
                rows.size() == 4
                        && rows.get(0).registro() == 1
                        && rows.get(0).columns().equals(java.util.List.of(
                                "Ana Teste", "12345678909", "ana@example.com", "Senha123"))
                        && rows.get(3).registro() == 4
                        && rows.get(3).columns().get(0).equals("Davi Teste")
        ));
        verifyNoInteractions(createUserUseCase);
    }

    @Test
    @DisplayName("deve retornar ApiErrorResponse quando CSV for malformado")
    void malformedCsvDoesNotAttemptAnyRegistration() {
        String csv = """
                    nome,cpf,email,senha
                    Ana Teste,12345678909,ana@example.com,Senha123
                    "Carla Teste,98765432100,carla@example.com,Senha789
                    """;

        ResponseEntity<?> response = controller().upload(csvFile(csv));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals(400, body.status());
        verifyNoInteractions(importUsersUseCase, createUserUseCase);
    }

    @Test
    @DisplayName("deve aceitar CSV com BOM do Excel e cabeçalhos com maiúsculas e espaços")
    void acceptsCsvWithBomAndCaseInsensitiveHeaders() {
        // \uFEFF simula o BOM gerado pelo Excel UTF-8
        String csvWithBom = "\uFEFF Nome , CPF , Email , Senha \n" +
                "Carlos Teste,12345678909,carlos@example.com,Senha123\n";

        when(importUsersUseCase.importUsers(anyList())).thenReturn(new ImportUsersResult(
                1, 1, 0, java.util.List.of(
                new ImportUserResult(1, "IMPORTADO", 10L, null)
        )
        ));

        ResponseEntity<?> response = controller().upload(csvFile(csvWithBom));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(importUsersUseCase).importUsers(argThat(rows ->
                rows.size() == 1
                        && rows.get(0).columns().get(0).equals("Carlos Teste")
        ));
    }

    @Test
    @DisplayName("deve retornar ApiErrorResponse quando o arquivo CSV estiver vazio")
    void emptyFileReturnsApiErrorResponse() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]
        );

        ResponseEntity<?> response = controller().upload(emptyFile);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiErrorResponse body = assertInstanceOf(ApiErrorResponse.class, response.getBody());
        assertEquals("O arquivo CSV está vazio", body.message());
        verifyNoInteractions(importUsersUseCase, createUserUseCase);
    }

    @Test
    @DisplayName("deve ignorar linhas em branco no arquivo CSV")
    void ignoresEmptyLinesInCsv() {
        String csvWithEmptyLines = """
                    nome,cpf,email,senha

                    Ana Teste,12345678909,ana@example.com,Senha123

                    """;

        when(importUsersUseCase.importUsers(anyList())).thenReturn(new ImportUsersResult(
                1, 1, 0, java.util.List.of(
                new ImportUserResult(1, "IMPORTADO", 1L, null)
        )
        ));

        ResponseEntity<?> response = controller().upload(csvFile(csvWithEmptyLines));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(importUsersUseCase).importUsers(argThat(rows -> rows.size() == 1));
    }

    private UserController controller() {
        return new UserController(
                createUserUseCase,
                getUsersUseCase,
                getUserByIdUseCase,
                updateUserUseCase,
                deleteUserUseCase,
                importUsersUseCase
        );
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }
}
