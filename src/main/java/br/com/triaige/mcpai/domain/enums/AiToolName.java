package br.com.triaige.mcpai.domain.enums;

import lombok.Getter;

/** ai_tool_calls.tool_name — T1 a T5 + llm_analysis. */
@Getter
public enum AiToolName {
    OCR("ocr"),
    ANONYMIZATION("anonymization"),
    ATTACHMENT_GROUPING("attachment_grouping"),
    EVIDENCE_SUMMARIZATION("evidence_summarization"),
    JURISPRUDENCE_QUERY("jurisprudence_query"),
    LLM_ANALYSIS("llm_analysis");

    private final String value;

    AiToolName(String value) {
        this.value = value;
    }

    public static AiToolName fromValue(String value) {
        for (AiToolName candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown tool name: " + value);
    }
}
