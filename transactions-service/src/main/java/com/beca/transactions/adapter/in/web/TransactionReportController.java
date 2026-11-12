package com.beca.transactions.adapter.in.web;

import com.beca.transactions.adapter.in.web.dto.ApiErrorResponse;
import com.beca.transactions.application.usecase.TransactionReportService;
import com.beca.transactions.domain.model.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Relatórios", description = "Exportação e relatórios financeiros")
@ApiResponse(
        responseCode = "503",
        description = "Serviço de autenticação do MS1 indisponível",
        content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ApiErrorResponse.class))
)
@RestController
@RequestMapping("/reports/transactions")
public class TransactionReportController {

    private final TransactionReportService reportService;

    public TransactionReportController(TransactionReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(
            summary = "Exportar relatório de transações em CSV",
            description = "Gera um relatório CSV consolidado com o histórico de transações do usuário no período especificado. Se as datas forem omitidas, utiliza o mês corrente."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Relatório CSV gerado com sucesso",
            content = @Content(mediaType = "text/csv")
    )
    @ApiResponse(
            responseCode = "400",
            description = "Intervalo de datas inválido",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Não autenticado",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))
    )
    @GetMapping(value = "/csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> downloadCsv(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Data inicial no formato ISO (YYYY-MM-DD)", example = "2026-11-01")
            @RequestParam(name = "dataInicio", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @Parameter(description = "Data final no formato ISO (YYYY-MM-DD)", example = "2026-11-30")
            @RequestParam(name = "dataFim", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim
    ) {
        byte[] csv = reportService.generateCsvReport(user.id(), dataInicio, dataFim);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transacoes.csv\"")
                .body(csv);
    }
}
