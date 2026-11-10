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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Map;

@Tag(name = "Usuários", description = "Operações de gerenciamento de usuários")
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

    @Operation(summary = "Cadastrar usuário")
    @ApiResponse(
            responseCode = "201",
            description = "Usuário cadastrado",
            headers = @Header(
                    name = "Location",
                    description = "Caminho do usuário cadastrado",
                    schema = @Schema(type = "string", example = "/users/1")
            ),
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class))
    )
    @ApiResponse(responseCode = "400", description = "Dados de cadastro inválidos",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
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

    @Operation(summary = "Listar usuários")
    @ApiResponse(responseCode = "200", description = "Lista de usuários",
            useReturnTypeSchema = true)
    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers() {
        List<UserResponse> users = getUsersUseCase.getUsers()
                .stream()
                .map(user -> toResponse(user))
                .toList();

        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Consultar usuário por ID")
    @ApiResponse(responseCode = "200", description = "Usuário encontrado",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "Identificador único do usuário", example = "1")
            @PathVariable Long id) {
        User user = getUserByIdUseCase.getUserById(id);
        return ResponseEntity.ok(toResponse(user));
    }

    @Operation(summary = "Atualizar nome e e-mail de um usuário")
    @ApiResponse(responseCode = "200", description = "Usuário atualizado",
            useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Dados de atualização inválidos",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já usado por outro usuário",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(
            @Parameter(description = "Identificador único do usuário", example = "1")
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        User user = updateUserUseCase.update(
                id,
                request.name(),
                request.email()
        );

        return ResponseEntity.ok(toResponse(user));
    }

    @Operation(summary = "Excluir usuário por ID")
    @ApiResponse(responseCode = "204", description = "Usuário excluído", content = @Content)
    @ApiResponse(responseCode = "404", description = "Usuário não encontrado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUserById(
            @Parameter(description = "Identificador único do usuário", example = "1")
            @PathVariable Long id) {
        deleteUserUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Importar usuários de um CSV",
            description = "Envie um CSV UTF-8 separado por vírgulas com o cabeçalho "
                    + "nome,cpf,email,senha. A resposta informa o resultado de cada registro."
    )
    @ApiResponse(responseCode = "200", description = "Arquivo processado, com resultado por registro",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UploadResponse.class)))
    @ApiResponse(responseCode = "400", description = "Arquivo vazio, ilegível ou com cabeçalho inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> upload(@Parameter(description = "Arquivo CSV com os usuários")
                                        @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(
                    400,
                    "Bad Request",
                    "O arquivo CSV está vazio",
                    Map.of("file", "O arquivo enviado não pode estar vazio")
            ));
        }

        CSVFormat format = CSVFormat.RFC4180.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get();

        try (Reader reader = new InputStreamReader(BOMInputStream.builder().setInputStream(file.getInputStream()).get(),
                StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)){
            List<String> expectedHeader = List.of("nome", "cpf", "email", "senha");
            List<String> actualHeader = parser.getHeaderNames().stream()
                    .map(name->name!=null ? name.trim().toLowerCase() : "")
                    .toList();

            if (!actualHeader.equals(expectedHeader)) {
                return ResponseEntity.badRequest().body(new ApiErrorResponse(
                        400,
                        "Bad Request",
                        "Cabeçalho inválido. Esperado: nome,cpf,email,senha",
                        Map.of("header", "Esperado: nome,cpf,email,senha")
                ));
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
            return ResponseEntity.badRequest().body(new ApiErrorResponse(
                    400,
                    "Bad Request",
                    "Não foi possível ler o arquivo CSV.",
                    Map.of("file", "Erro ao processar leitura do arquivo")
            ));
        }
        catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiErrorResponse(
                    400,
                    "Bad Request",
                    "Cabeçalho do CSV é inválido.",
                    Map.of("header", "Formato de cabeçalho inválido")
            ));
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
