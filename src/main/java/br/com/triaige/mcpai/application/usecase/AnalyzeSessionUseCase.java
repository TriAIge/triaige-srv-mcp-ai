package br.com.triaige.mcpai.application.usecase;

import br.com.triaige.mcpai.api.dto.request.AnalysisRequest;
import br.com.triaige.mcpai.api.dto.response.AnalysisResponse;
import br.com.triaige.mcpai.api.dto.response.RelatorioEstruturado;
import br.com.triaige.mcpai.application.service.AiToolCallService;
import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.enums.AiToolName;
import br.com.triaige.mcpai.domain.enums.AiToolProvider;
import br.com.triaige.mcpai.domain.exception.AnalysisException;
import br.com.triaige.mcpai.domain.exception.AnalysisTimeoutException;
import br.com.triaige.mcpai.domain.exception.InvalidReportFormatException;
import br.com.triaige.mcpai.domain.exception.NoProcessableContentException;
import br.com.triaige.mcpai.infrastructure.config.AiProperties;
import br.com.triaige.mcpai.infrastructure.gemini.GeminiReasoningService;
import br.com.triaige.mcpai.infrastructure.gemini.GeminiReportDto;
import br.com.triaige.mcpai.infrastructure.gemini.ReasoningResult;
import br.com.triaige.mcpai.infrastructure.s3.TrustedDocumentPayload;
import br.com.triaige.mcpai.infrastructure.s3.TrustedDocumentReader;
import br.com.triaige.mcpai.shared.metrics.McpMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Orquestra o {@code POST /api/ai/v1/analyze}: valida
 * processedGroups, lê os objetos S3 trusted (já escritos por este mesmo serviço),
 * monta o contexto, raciocina com o Gemini (incluindo function calling em processo de
 * jurisprudence_query), valida o relatório contra o schema e grava a auditoria
 * em ai_tool_calls (tool_name=llm_analysis). Toda a execução roda sob um timeout total
 * ({@code ai.analysis.timeout-ms}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeSessionUseCase {

    private static final Set<String> VALID_CLASSIFICACOES =
            Set.of("viavel", "inviavel", "necessita_mais_documentos");

    private static final Set<String> VALID_NIVEIS_ATENDIMENTO = Set.of("junior", "pleno", "socio");

    private static final String SCHEMA_VERSION_V2 = "2.0";

    private final TrustedDocumentReader trustedDocumentReader;
    private final GeminiReasoningService geminiReasoningService;
    private final AiToolCallService aiToolCallService;
    private final AiProperties aiProperties;
    private final McpMetrics metrics;
    private final ObjectMapper objectMapper;

    public AnalysisResponse execute(AnalysisRequest request) {
        long timeoutMs = aiProperties.getAnalysis().getTimeoutMs();
        CompletableFuture<AnalysisResponse> future = CompletableFuture.supplyAsync(() -> doExecute(request));

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            metrics.incrementAnalysisFailure("ANALYSIS_TIMEOUT");
            throw (AnalysisTimeoutException) new AnalysisTimeoutException().withSessionId(request.getSessionId());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AnalysisException analysisException) {
                throw analysisException;
            }
            throw new IllegalStateException("Falha inesperada durante a análise", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Análise interrompida", e);
        }
    }

    private AnalysisResponse doExecute(AnalysisRequest request) {
        long start = System.currentTimeMillis();
        UUID sessionId = request.getSessionId();

        try {
            List<AnalysisRequest.ProcessedGroupDto> processedGroups = request.getProcessedGroups();
            if (processedGroups == null || processedGroups.isEmpty()) {
                throw new NoProcessableContentException();
            }

            ContextoMontado contextoMontado = buildContext(processedGroups);
            String contexto = contextoMontado.texto();

            AiToolCall llmCall = aiToolCallService.start(sessionId, AiToolName.LLM_ANALYSIS, AiToolProvider.GEMINI,
                    Map.of("model", aiProperties.getGemini().getModel(),
                            "processedGroupsCount", processedGroups.size()));

            RelatorioEstruturado relatorio;
            ReasoningResult reasoningResult;
            try {
                reasoningResult = reasonWithFormatRetry(sessionId, request, contexto);
                relatorio = buildRelatorio(request, reasoningResult, processedGroups, contextoMontado.nomesArquivoPorGrupo());
            } catch (RuntimeException e) {
                aiToolCallService.completeFailure(llmCall.getId(), e.getMessage());
                metrics.incrementAnalysisFailure(e instanceof AnalysisException analysisEx ? analysisEx.getCode() : "UNEXPECTED");
                throw e;
            }

            aiToolCallService.completeSuccess(llmCall.getId(), Map.of(
                    "tokensIn", reasoningResult.tokensIn(),
                    "tokensOut", reasoningResult.tokensOut(),
                    "latencyMs", System.currentTimeMillis() - start,
                    "finishReason", reasoningResult.finishReason()));

            metrics.incrementAnalysisSuccess();
            metrics.recordAnalysisLatency(System.currentTimeMillis() - start);
            metrics.recordJurisprudenceToolCallsPerAnalysis(reasoningResult.toolCallsUsed());

            return AnalysisResponse.builder()
                    .sessionId(sessionId)
                    .correlationId(request.getCorrelationId())
                    .status("COMPLETED")
                    .relatorioEstruturado(relatorio)
                    .geminiToolCallId(llmCall.getId())
                    .jurisprudenceCallId(reasoningResult.jurisprudenceCallId())
                    .geradoEm(LocalDateTime.now())
                    .build();
        } catch (AnalysisException e) {
            throw (AnalysisException) e.withSessionId(sessionId);
        }
    }

    /**
     * Regra: 1 retry de geração se o
     * relatório não validar — não só o parse estrutural (JSON inválido), mas também
     * {@code confianca} fora de [0,100] e {@code nivelAtendimentoSugerido} fora do enum. Se
     * persistir após o retry, INVALID_REPORT_FORMAT.
     */
    private ReasoningResult reasonWithFormatRetry(UUID sessionId, AnalysisRequest request, String contexto) {
        ReasoningResult first = geminiReasoningService.reason(sessionId, request.getLegalCase(), contexto);
        if (isValidFormat(first.reportJsonText())) {
            return first;
        }
        log.warn("Relatório do Gemini fora do formato esperado, tentando 1 retry de geração: sessionId={}", sessionId);
        metrics.incrementInvalidReportFormat();
        ReasoningResult retry = geminiReasoningService.reason(sessionId, request.getLegalCase(), contexto);
        if (isValidFormat(retry.reportJsonText())) {
            return retry;
        }
        metrics.incrementInvalidReportFormat();
        throw new InvalidReportFormatException("Relatório do Gemini não pôde ser interpretado após 1 retry de geração");
    }

    private boolean isValidFormat(String reportJsonText) {
        GeminiReportDto raw;
        try {
            raw = objectMapper.readValue(stripMarkdownFence(reportJsonText), GeminiReportDto.class);
        } catch (Exception e) {
            return false;
        }
        return isConfiancaValid(raw) && isNivelAtendimentoValid(raw);
    }

    /**
     * O Gemini às vezes devolve o relatório envolto em cerca de código markdown
     * ({@code ```json ... ```}) mesmo com {@code responseMimeType=application/json} pedido —
     * comportamento não-determinístico conhecido do modelo, não um erro de geração. Sem isso,
     * um relatório perfeitamente válido era rejeitado só por causa do backtick inicial, gastando
     * o único retry disponível e chegando a INVALID_REPORT_FORMAT com conteúdo bom.
     */
    private String stripMarkdownFence(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        trimmed = firstNewline >= 0 ? trimmed.substring(firstNewline + 1) : trimmed.substring(3);
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.strip();
    }

    private boolean isConfiancaValid(GeminiReportDto raw) {
        if (raw.getAvaliacaoCriticidade() == null) {
            return true;
        }
        RelatorioEstruturado.AvaliacaoCriticidade av = raw.getAvaliacaoCriticidade();
        return isConfiancaValid(av.getCriticidade() == null ? null : av.getCriticidade().getConfianca())
                && isConfiancaValid(av.getComplexidade() == null ? null : av.getComplexidade().getConfianca())
                && isConfiancaValid(av.getImpactoFinanceiroEstimado() == null ? null
                        : av.getImpactoFinanceiroEstimado().getConfianca());
    }

    private boolean isConfiancaValid(Integer confianca) {
        return confianca == null || (confianca >= 0 && confianca <= 100);
    }

    private boolean isNivelAtendimentoValid(GeminiReportDto raw) {
        if (raw.getClassificacaoInicial() == null || raw.getClassificacaoInicial().getNivelAtendimentoSugerido() == null) {
            return true;
        }
        return VALID_NIVEIS_ATENDIMENTO.contains(raw.getClassificacaoInicial().getNivelAtendimentoSugerido());
    }

    private RelatorioEstruturado buildRelatorio(AnalysisRequest request, ReasoningResult reasoningResult,
                                                 List<AnalysisRequest.ProcessedGroupDto> processedGroups,
                                                 Map<UUID, String> nomesArquivoPorGrupo) {
        GeminiReportDto raw;
        try {
            raw = objectMapper.readValue(stripMarkdownFence(reasoningResult.reportJsonText()), GeminiReportDto.class);
        } catch (Exception e) {
            throw new InvalidReportFormatException("Relatório do Gemini não pôde ser interpretado: " + e.getMessage());
        }
        validate(raw, reasoningResult, processedGroups);

        List<RelatorioEstruturado.EvidenciaAnalisada> evidencias = raw.getEvidenciasAnalisadas() == null ? null
                : raw.getEvidenciasAnalisadas().stream()
                        .map(evidencia -> evidencia.toBuilder()
                                .nomeArquivoOriginal(nomesArquivoPorGrupo.get(evidencia.getAttachmentGroupId()))
                                .build())
                        .toList();

        return RelatorioEstruturado.builder()
                .schemaVersion(SCHEMA_VERSION_V2)
                .sessionId(request.getSessionId())
                .protocolo(request.getProtocolo())
                .areaJuridica(request.getLegalCase().getAreaJuridica())
                .tipoCaso(request.getLegalCase().getTipoCaso())
                .resumoExecutivo(raw.getResumoExecutivo())
                .fundamentacaoJuridica(raw.getFundamentacaoJuridica())
                .evidenciasAnalisadas(evidencias)
                .jurisprudenciaCitada(raw.getJurisprudenciaCitada())
                .riscosIdentificados(raw.getRiscosIdentificados())
                .recomendacao(raw.getRecomendacao())
                .classificacaoInicial(raw.getClassificacaoInicial())
                .avaliacaoCriticidade(raw.getAvaliacaoCriticidade())
                .partesExtraidas(raw.getPartesExtraidas())
                .controleDePrazos(raw.getControleDePrazos())
                .resumoEstruturado(raw.getResumoEstruturado())
                .metadados(RelatorioEstruturado.Metadados.builder()
                        .modeloUtilizado(aiProperties.getGemini().getModel())
                        .toolCallsUsados(reasoningResult.toolCallsUsed())
                        .geradoEm(LocalDateTime.now())
                        .tempoProcessamentoMs(0)
                        .build())
                .build();
    }

    /** Regras de validação. */
    private void validate(GeminiReportDto raw, ReasoningResult reasoningResult,
                           List<AnalysisRequest.ProcessedGroupDto> processedGroups) {
        if (raw.getRecomendacao() == null
                || !VALID_CLASSIFICACOES.contains(raw.getRecomendacao().getClassificacao())) {
            throw new InvalidReportFormatException(
                    "recomendacao.classificacao ausente ou fora do enum viavel|inviavel|necessita_mais_documentos");
        }

        Set<UUID> evidenciados = raw.getEvidenciasAnalisadas() == null ? Set.of()
                : raw.getEvidenciasAnalisadas().stream()
                        .map(RelatorioEstruturado.EvidenciaAnalisada::getAttachmentGroupId)
                        .collect(Collectors.toSet());
        Set<UUID> recebidos = processedGroups.stream()
                .map(AnalysisRequest.ProcessedGroupDto::getAttachmentGroupId)
                .collect(Collectors.toSet());
        if (!evidenciados.containsAll(recebidos)) {
            throw new InvalidReportFormatException(
                    "evidenciasAnalisadas não cobre todos os attachmentGroupId recebidos em processedGroups");
        }

        boolean temJurisprudencia = raw.getJurisprudenciaCitada() != null && !raw.getJurisprudenciaCitada().isEmpty();
        if (temJurisprudencia && reasoningResult.jurisprudenceCallId() == null) {
            throw new InvalidReportFormatException(
                    "jurisprudenciaCitada não vazio mas nenhuma chamada jurisprudence_query bem-sucedida foi registrada");
        }
    }

    /**
     * Lê cada objeto trusted e monta o contexto ordenado
     * por attachmentGroupId (ordem de chegada). Na mesma passagem, também coleta os
     * nomes de arquivo originais de cada grupo (NOME_ARQUIVO_NN) — nunca vai
     * para o prompt (custaria tokens e risco de eco incorreto), só é injetado no relatório
     * depois do parse, em {@link #buildRelatorio}.
     */
    private ContextoMontado buildContext(List<AnalysisRequest.ProcessedGroupDto> processedGroups) {
        StringBuilder sb = new StringBuilder();
        Map<UUID, String> nomesArquivoPorGrupo = new java.util.HashMap<>();
        for (AnalysisRequest.ProcessedGroupDto group : processedGroups) {
            TrustedDocumentPayload payload = trustedDocumentReader.read(group.getTrustedBucket(), group.getTrustedObjectKey());
            sb.append("--- Grupo ").append(group.getAttachmentGroupId())
                    .append(" (").append(group.getTipoDocumento()).append(") ---\n")
                    .append(payload.getTextoAnonimizado())
                    .append("\n\n");

            if (payload.getPartesOrigem() != null) {
                String nomes = payload.getPartesOrigem().stream()
                        .map(TrustedDocumentPayload.PartOrigem::getNomeArquivoOriginal)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.joining(", "));
                nomesArquivoPorGrupo.put(group.getAttachmentGroupId(), nomes);
            }
        }
        return new ContextoMontado(sb.toString(), nomesArquivoPorGrupo);
    }

    private record ContextoMontado(String texto, Map<UUID, String> nomesArquivoPorGrupo) {
    }
}
