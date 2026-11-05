package com.beca.userservice.adapter.in.web;

import com.beca.userservice.adapter.in.web.dto.*;
import com.beca.userservice.application.usecase.*;
import com.beca.userservice.domain.model.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import java.nio.charset.StandardCharsets;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;

@RestController
@RequestMapping("/users")
public class UserController {
    private final CreateUserUseCase createUserUseCase;
    private final GetUsersUseCase getUsersUseCase;
    private final GetUserByIdUseCase getUserByIdUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final ImportUsersUseCase importUsersUseCase;

    public UserController(CreateUserUseCase createUserUseCase, GetUsersUseCase getUsersUseCase, GetUserByIdUseCase getUserByIdUseCase,
                          UpdateUserUseCase updateUserUseCase, DeleteUserUseCase deleteUserUseCase, ImportUsersUseCase importUsersUseCase) {
        this.createUserUseCase = createUserUseCase;
        this.getUsersUseCase = getUsersUseCase;
        this.getUserByIdUseCase = getUserByIdUseCase;
        this.updateUserUseCase = updateUserUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.importUsersUseCase = importUsersUseCase;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User user = createUserUseCase.create(
                request.name(),
                request.cpf(),
                request.email(),
                request.password()
        );

        UserResponse response = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );

        URI location = URI.create("/users/" + user.getId());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        List<UserResponse> users = getUsersUseCase.getUsers()
                .stream()
                .map(user -> toResponse(user))
                .toList();

        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        User user = getUserByIdUseCase.getUserById(id);
        return ResponseEntity.ok(toResponse(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User user = updateUserUseCase.update(
                id,
                request.name(),
                request.email()
        );

        return ResponseEntity.ok(toResponse(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserById(@PathVariable Long id) {
        deleteUserUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        if(file.isEmpty()) return ResponseEntity.badRequest().body("O arquivo CSV está vazio");

        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (Reader reader = new InputStreamReader(BOMInputStream.builder().setInputStream(file.getInputStream()).get(),
                StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)){
            List<String> expectedHeader = List.of("nome", "cpf", "email", "senha");
            List<String> actualHeader = parser.getHeaderNames().stream()
                    .map(name->name!=null ? name.trim().toLowerCase() : "")
                    .toList();

            if(!actualHeader.equals(expectedHeader)){
                return ResponseEntity.badRequest().body("Cabeçalho inválido. Esperado: nome,cpf,email,senha");
            }

            List<CSVRecord> records = parser.getRecords();
            List<ImportUserRow> rows = new ArrayList<>(records.size());
            int registro = 0;

            for(CSVRecord record : records){
                final List<String> columns = new ArrayList<>();
                record.forEach(value -> columns.add(value!=null ? value.trim() : ""));
                rows.add(new ImportUserRow(++registro, columns));
            }

            ImportUsersResult res = importUsersUseCase.importUsers(rows);

            List<UploadLineResult> resultados = res.resultados()
                    .stream().map(
                            item->new UploadLineResult(
                                    item.registro(),
                                    item.status(),
                                    item.userId(),
                                    item.reason()
                            )
                    ).toList();

            return ResponseEntity.ok(new UploadResponse(
                    res.total(), res.importados(), res.falhas(), resultados
            ));
        }  catch (IOException|UncheckedIOException e) {
            return ResponseEntity.badRequest()
                    .body("Não foi possivel ler o CSV");
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Cabeçalho do CSV é inválido");
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
