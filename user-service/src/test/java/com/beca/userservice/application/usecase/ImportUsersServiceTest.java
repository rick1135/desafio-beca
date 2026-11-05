package com.beca.userservice.application.usecase;

import com.beca.userservice.application.exception.DuplicateEmailException;
import com.beca.userservice.domain.model.User;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportUsersServiceTest {
    private static ValidatorFactory validatorFactory;

    @Mock
    private CreateUserUseCase createUserUseCase;

    @BeforeAll
    static void createValidatorFactory() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void importsValidRowsAndContinuesAfterInvalidAndDuplicateRows() {
        Validator validator = validatorFactory.getValidator();
        ImportUsersService service = new ImportUsersService(createUserUseCase, validator);

        List<ImportUserRow> rows = List.of(
                row(1, "Ana", "12345678909", "ana@example.com", "Senha123"),
                row(2, "Bruno", "123456789", "bruno@example.com", "Senha123"),
                new ImportUserRow(3, List.of("Carla", "98765432100", "carla@example.com")),
                row(4, "Davi", "11122233344", "ana@example.com", "Senha123"),
                row(5, "Eva", "55566677788", "eva@example.com", "Senha123")
        );

        when(createUserUseCase.create("Ana", "12345678909", "ana@example.com", "Senha123"))
                .thenReturn(new User(1L, "Ana", "12345678909", "ana@example.com", "hash"));
        doThrow(new DuplicateEmailException()).when(createUserUseCase)
                .create("Davi", "11122233344", "ana@example.com", "Senha123");
        when(createUserUseCase.create("Eva", "55566677788", "eva@example.com", "Senha123"))
                .thenReturn(new User(2L, "Eva", "55566677788", "eva@example.com", "hash"));

        ImportUsersResult result = service.importUsers(rows);

        assertEquals(5, result.total());
        assertEquals(2, result.importados());
        assertEquals(3, result.falhas());
        assertEquals(5, result.resultados().size());
        assertEquals(new ImportUserResult(1, "IMPORTADO", 1L, null), result.resultados().get(0));
        assertEquals("FALHA", result.resultados().get(1).status());
        assertTrue(result.resultados().get(1).reason().contains("cpf"));
        assertEquals("Quantidade incorreta de colunas", result.resultados().get(2).reason());
        assertEquals(new ImportUserResult(4, "FALHA", null, "EMAIL_DUPLICADO"), result.resultados().get(3));
        assertEquals(new ImportUserResult(5, "IMPORTADO", 2L, null), result.resultados().get(4));

        verify(createUserUseCase).create("Ana", "12345678909", "ana@example.com", "Senha123");
        verify(createUserUseCase).create("Davi", "11122233344", "ana@example.com", "Senha123");
        verify(createUserUseCase).create("Eva", "55566677788", "eva@example.com", "Senha123");
        verifyNoMoreInteractions(createUserUseCase);
    }

    @Test
    @DisplayName("não deve derrubar o lote quando ocorrer erro inesperado em uma linha")
    void continuesImportWhenUnexpectedExceptionOccursOnRow() {
        Validator validator = validatorFactory.getValidator();
        ImportUsersService service = new ImportUsersService(createUserUseCase, validator);

        List<ImportUserRow> rows = List.of(
                row(1, "Ana", "12345678909", "ana@example.com", "Senha123"),
                row(2, "Carlos", "98765432100", "carlos@example.com", "Senha123"),
                row(3, "Bia", "11122233344", "bia@example.com", "Senha123")
        );

        when(createUserUseCase.create("Ana", "12345678909", "ana@example.com", "Senha123"))
                .thenReturn(new User(1L, "Ana", "12345678909", "ana@example.com", "hash"));

        doThrow(new RuntimeException("Falha de conexão com banco de dados"))
                .when(createUserUseCase)
                .create("Carlos", "98765432100", "carlos@example.com", "Senha123");

        when(createUserUseCase.create("Bia", "11122233344", "bia@example.com", "Senha123"))
                .thenReturn(new User(2L, "Bia", "11122233344", "bia@example.com", "hash"));

        ImportUsersResult result = service.importUsers(rows);

        assertEquals(3, result.total());
        assertEquals(2, result.importados());
        assertEquals(1, result.falhas());
        assertEquals("Falha de conexão com banco de dados", result.resultados().get(1).reason());
        assertEquals("FALHA", result.resultados().get(1).status());
        assertEquals("IMPORTADO", result.resultados().get(2).status());
    }

    private ImportUserRow row(int registro, String name, String cpf, String email, String password) {
        return new ImportUserRow(registro, List.of(name, cpf, email, password));
    }
}
