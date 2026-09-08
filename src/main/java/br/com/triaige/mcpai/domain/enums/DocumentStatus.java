package br.com.triaige.mcpai.domain.enums;

/** Máquina de estados de legal_documents.status a partir de QUEUED (spec seção 4). */
public enum DocumentStatus {
    QUEUED,
    OCR_IN_PROGRESS,
    OCR_DONE,
    ANONYMIZED,
    GROUPED,
    TRUSTED,
    OCR_FAILED,
    PROCESSING_FAILED
}
