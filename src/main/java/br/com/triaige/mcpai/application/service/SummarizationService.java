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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * T4 — Resumo de Evidências longas, condicional. Sumarização
 * extrativa heurística, SEM chamada a LLM externo: pontua
 * sentenças por presença de termos jurídicos/datas/valores monetários e por posição
 * (primeira/última página com peso maior), seleciona as de maior pontuação até o teto
 * configurado, e preserva a ORDEM ORIGINAL das sentenças selecionadas (não reordena por
 * score) para manter legibilidade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SummarizationService {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern DATE_PATTERN = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    private static final Pattern MONETARY_PATTERN = Pattern.compile("R\\$\\s?\\d");

    private static final Set<String> LEGAL_TERMS = Set.of(
            "contrato", "clausula", "dano", "danos", "prazo", "indenizacao", "obrigacao",
            "rescisao", "multa", "juros", "sentenca", "acordo", "responsabilidade", "processo",
            "reu", "autor", "peticao", "recurso", "prova", "provas", "testemunha", "laudo");

    private final McpProperties mcpProperties;
    private final ProcessingStepService processingStepService;
    private final AiToolCallService aiToolCallService;

    public SummarizationResult summarize(UUID sessionId, UUID attachmentGroupId, String text) {
        ProcessingStep step = processingStepService.startGroupStep(sessionId, attachmentGroupId,
                ProcessingStepName.EVIDENCE_SUMMARIZATION);
        AiToolCall call = aiToolCallService.start(sessionId, AiToolName.EVIDENCE_SUMMARIZATION,
                AiToolProvider.INTERNAL_HEURISTIC, Map.of("attachmentGroupId", attachmentGroupId));

        try {
            int originalCharCount = text.length();
            long maxChars = mcpProperties.getSummarization().getSummaryMaxTokens() * 4;

            List<String> sentences = splitSentences(text);
            List<ScoredSentence> scored = score(sentences);

            String summary = selectPreservingOrder(scored, maxChars);
            int summaryCharCount = summary.length();

            SummarizationResult result = new SummarizationResult(summary, originalCharCount, summaryCharCount);

            processingStepService.complete(step.getId());
            aiToolCallService.completeSuccess(call.getId(), Map.of(
                    "originalCharCount", originalCharCount,
                    "summaryCharCount", summaryCharCount,
                    "taxaCompressao", result.compressionRate()));

            log.info("Summarization completed: sessionId={}, attachmentGroupId={}, originalChars={}, summaryChars={}",
                    sessionId, attachmentGroupId, originalCharCount, summaryCharCount);

            return result;
        } catch (RuntimeException e) {
            log.error("Summarization failed: sessionId={}, attachmentGroupId={}, textLength={}, reason={}",
                    sessionId, attachmentGroupId, text.length(), e.getMessage(), e);
            processingStepService.fail(step.getId(), e.getMessage());
            aiToolCallService.completeFailure(call.getId(), e.getMessage());
            throw e;
        }
    }

    private List<String> splitSentences(String text) {
        String[] parts = SENTENCE_SPLIT.split(text.trim());
        List<String> sentences = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                sentences.add(part.trim());
            }
        }
        return sentences;
    }

    private List<ScoredSentence> score(List<String> sentences) {
        int total = sentences.size();
        int edgeZoneSize = Math.max(1, total / 10);
        List<ScoredSentence> scored = new ArrayList<>(total);

        for (int i = 0; i < total; i++) {
            String sentence = sentences.get(i);
            int score = 0;

            String normalized = stripAccents(sentence).toLowerCase();
            for (String term : LEGAL_TERMS) {
                if (normalized.contains(term)) {
                    score++;
                }
            }
            if (DATE_PATTERN.matcher(sentence).find()) {
                score++;
            }
            if (MONETARY_PATTERN.matcher(sentence).find()) {
                score++;
            }
            if (i < edgeZoneSize || i >= total - edgeZoneSize) {
                score += 2;
            }

            scored.add(new ScoredSentence(i, sentence, score));
        }
        return scored;
    }

    /** Seleciona por score decrescente até o teto de caracteres, depois reordena por posição original. */
    private String selectPreservingOrder(List<ScoredSentence> scored, long maxChars) {
        List<ScoredSentence> byScore = new ArrayList<>(scored);
        byScore.sort(Comparator.comparingInt(ScoredSentence::score).reversed());

        List<ScoredSentence> selected = new ArrayList<>();
        long chars = 0;
        for (ScoredSentence candidate : byScore) {
            long candidateLength = candidate.sentence().length() + 1;
            if (!selected.isEmpty() && chars + candidateLength > maxChars) {
                continue;
            }
            selected.add(candidate);
            chars += candidateLength;
        }

        selected.sort(Comparator.comparingInt(ScoredSentence::originalIndex));

        StringBuilder sb = new StringBuilder();
        for (ScoredSentence s : selected) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(s.sentence());
        }
        return sb.toString();
    }

    private String stripAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private record ScoredSentence(int originalIndex, String sentence, int score) {
    }
}
