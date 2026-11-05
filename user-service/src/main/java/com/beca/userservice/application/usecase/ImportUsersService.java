package com.beca.userservice.application.usecase;

import com.beca.userservice.application.exception.DuplicateEmailException;
import com.beca.userservice.domain.model.User;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportUsersService implements ImportUsersUseCase {
    private final CreateUserUseCase createUserUseCase;
    private final Validator validator;
    private static final Logger log = LoggerFactory.getLogger(ImportUsersService.class);

    public ImportUsersService(CreateUserUseCase createUserUseCase, Validator validator) {
        this.createUserUseCase = createUserUseCase;
        this.validator = validator;
    }

    @Override
    public ImportUsersResult importUsers(List<ImportUserRow> rows){
        List<ImportUserResult> resultados = new ArrayList<>();
        int imports = 0;

        for (ImportUserRow row : rows){
            int registro=row.registro();
            List<String> columns = row.columns();

            if(columns.size()!=4){
                resultados.add(new ImportUserResult(
                        registro, "FALHA", null,
                        "Quantidade incorreta de colunas"
                        ));
                continue;
            }

            NewUserData data = new NewUserData(
                    columns.get(0) != null ? columns.get(0).trim() : null,
                    columns.get(1) != null ? columns.get(1).trim() : null,
                    columns.get(2) != null ? columns.get(2).trim() : null,
                    columns.get(3) != null ? columns.get(3).trim() : null
            );

            Set<ConstraintViolation<NewUserData>> violations = validator.validate(data);
            if(!violations.isEmpty()) {
                String motivo = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .sorted()
                        .collect(Collectors.joining("; "));
                resultados.add(new ImportUserResult(
                        registro, "FALHA", null, "Motivo: " + motivo
                ));
                continue;
            }
            try{
                User user = createUserUseCase.create(
                        data.name(),
                        data.cpf(),
                        data.email(),
                        data.password()
                );

                imports++;
                resultados.add(new ImportUserResult(
                        registro, "IMPORTADO", user.getId(), null
                ));
            } catch(DuplicateEmailException e){
                resultados.add(new ImportUserResult(
                        registro, "FALHA", null, "EMAIL_DUPLICADO"
                ));
            }
            catch (Exception e) {
                log.error("Falha inesperada ao importar usuário na linha {}", registro, e);
                resultados.add(new ImportUserResult(
                        registro, "FALHA", null, "Erro inesperado ao processar registro"
                ));
            }
        }
        return new ImportUsersResult(
                rows.size(), imports, rows.size()-imports, resultados
        );
    }
}
