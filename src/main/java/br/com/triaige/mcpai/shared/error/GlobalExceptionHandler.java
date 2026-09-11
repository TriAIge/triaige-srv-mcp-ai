package br.com.triaige.mcpai.shared.error;

import br.com.triaige.mcpai.domain.exception.AnalysisException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Trata exceções do endpoint REST de negócio {@code POST /api/ai/v1/analyze} — o
 * único deste serviço; o pipeline T1-T5 (SQS/MCP) não passa por aqui, tem seu próprio
 * tratamento local de erro em cada consumidor/tool.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AnalysisException.class)
    public ResponseEntity<AnalysisErrorResponse> handleAnalysisException(AnalysisException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("{}: {} - path={}", ex.getCode(), ex.getMessage(), request.getRequestURI(), ex);
        } else {
            log.warn("{}: {} - path={}", ex.getCode(), ex.getMessage(), request.getRequestURI());
        }
        AnalysisErrorResponse body = AnalysisErrorResponse.builder()
                .error(ex.getCode())
                .status(ex.isFailedStatus() ? "FAILED" : null)
                .sessionId(ex.getSessionId())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AnalysisErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                                    HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {} - path={}", message, request.getRequestURI());
        return ResponseEntity.badRequest().body(AnalysisErrorResponse.builder().error("VALIDATION_ERROR").build());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<AnalysisErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
                                                                       HttpServletRequest request) {
        log.warn("Missing request header: {} - path={}", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.badRequest().body(AnalysisErrorResponse.builder().error("VALIDATION_ERROR").build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AnalysisErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: {} - path={}", ex.getMessage(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AnalysisErrorResponse.builder().error("INTERNAL_ERROR").build());
    }
}
