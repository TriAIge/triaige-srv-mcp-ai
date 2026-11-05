package br.com.triaige.mcpai.api.controller;

import br.com.triaige.mcpai.api.dto.request.AnalysisRequest;
import br.com.triaige.mcpai.api.dto.response.AnalysisResponse;
import br.com.triaige.mcpai.application.service.IdempotencyService;
import br.com.triaige.mcpai.application.usecase.AnalyzeSessionUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Chamado pelo triaige-srv-orchestrator após receber um mcp-result COMPLETED/PARTIALLY_COMPLETED. */
@RestController
@RequestMapping("/api/ai/v1")
@RequiredArgsConstructor
public class AnalysisController {

    private static final String ENDPOINT_KEY = "POST /api/ai/v1/analyze";

    private final IdempotencyService idempotencyService;
    private final AnalyzeSessionUseCase analyzeSessionUseCase;

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyze(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AnalysisRequest request) {
        return idempotencyService.execute(idempotencyKey, ENDPOINT_KEY, request, AnalysisResponse.class,
                () -> ResponseEntity.status(HttpStatus.OK).body(analyzeSessionUseCase.execute(request)));
    }
}
