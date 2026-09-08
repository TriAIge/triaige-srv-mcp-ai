package br.com.triaige.mcpai.domain.enums;

import lombok.Getter;

@Getter
public enum ProcessingStepName {
    OCR("ocr"),
    ANONYMIZATION("anonymization"),
    ATTACHMENT_GROUPING("attachment_grouping"),
    EVIDENCE_SUMMARIZATION("evidence_summarization");

    private final String value;

    ProcessingStepName(String value) {
        this.value = value;
    }

    public static ProcessingStepName fromValue(String value) {
        for (ProcessingStepName candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown processing step name: " + value);
    }
}
