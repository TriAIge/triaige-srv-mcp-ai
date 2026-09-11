package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.AiToolCall;
import br.com.triaige.mcpai.domain.entity.ProcessingStep;
import br.com.triaige.mcpai.domain.enums.AiToolName;
import br.com.triaige.mcpai.domain.enums.AiToolProvider;
import br.com.triaige.mcpai.domain.enums.ProcessingStepName;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * T3 — Agrupamento de Anexos. Concatena as partes já ANONYMIZED de um
 * attachment_group_id na ordem de partNumber, e decide o roteamento T3->T4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentGroupingService {

    private final McpProperties mcpProperties;
    private final ProcessingStepService processingStepService;
    private final AiToolCallService aiToolCallService;

    public GroupingResult group(UUID sessionId, UUID attachmentGroupId, List<GroupPart> parts) {
        ProcessingStep step = processingStepService.startGroupStep(sessionId, attachmentGroupId,
                ProcessingStepName.ATTACHMENT_GROUPING);
        AiToolCall call = aiToolCallService.start(sessionId, AiToolName.ATTACHMENT_GROUPING, AiToolProvider.INTERNAL,
                Map.of("attachmentGroupId", attachmentGroupId, "partesCount", parts.size()));

        try {
            List<GroupPart> ordered = parts.stream()
                    .sorted(Comparator.comparingInt(GroupPart::partNumber))
                    .toList();

            StringBuilder sb = new StringBuilder();
            long totalPages = 0;
            for (GroupPart part : ordered) {
                sb.append("\n\n--- parte ").append(part.partNumber()).append(" ---\n\n");
                sb.append(part.anonymizedText());
                totalPages += part.pages();
            }
            String concatenated = sb.toString();
            long totalChars = concatenated.length();

            McpProperties.Summarization summarizationConfig = mcpProperties.getSummarization();
            long estimatedTokens = totalChars / 4;
            boolean routeToSummarization = estimatedTokens > summarizationConfig.getThresholdTokens()
                    || totalPages > summarizationConfig.getThresholdPages();

            processingStepService.complete(step.getId());
            aiToolCallService.completeSuccess(call.getId(), Map.of(
                    "caracteresTotais", totalChars,
                    "paginasTotais", totalPages,
                    "rotaEscolhida", routeToSummarization ? "evidence_summarization" : "s3_trusted"));

            log.info("Attachment grouping completed: sessionId={}, attachmentGroupId={}, parts={}, totalChars={}, "
                            + "totalPages={}, routeToSummarization={}",
                    sessionId, attachmentGroupId, ordered.size(), totalChars, totalPages, routeToSummarization);

            return new GroupingResult(concatenated, totalChars, totalPages, routeToSummarization);
        } catch (RuntimeException e) {
            log.error("Attachment grouping failed: sessionId={}, attachmentGroupId={}, partsCount={}, reason={}",
                    sessionId, attachmentGroupId, parts.size(), e.getMessage(), e);
            processingStepService.fail(step.getId(), e.getMessage());
            aiToolCallService.completeFailure(call.getId(), e.getMessage());
            throw e;
        }
    }
}
