package br.com.triaige.mcpai.application.service;

import br.com.triaige.mcpai.domain.entity.ProcessingStep;
import br.com.triaige.mcpai.domain.enums.ProcessingStepName;
import br.com.triaige.mcpai.domain.enums.ProcessingStepStatus;
import br.com.triaige.mcpai.infrastructure.persistence.ProcessingStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Um registro por etapa (spec seção 5). ocr/anonymization são nível documento
 * (documentId preenchido); attachment_grouping/evidence_summarization são nível grupo
 * (attachmentGroupId preenchido) — nunca os dois ao mesmo tempo.
 */
@Service
@RequiredArgsConstructor
public class ProcessingStepService {

    private final ProcessingStepRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessingStep startDocumentStep(UUID sessionId, UUID documentId, ProcessingStepName stepName) {
        return start(sessionId, documentId, null, stepName);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessingStep startGroupStep(UUID sessionId, UUID attachmentGroupId, ProcessingStepName stepName) {
        return start(sessionId, null, attachmentGroupId, stepName);
    }

    private ProcessingStep start(UUID sessionId, UUID documentId, UUID attachmentGroupId, ProcessingStepName stepName) {
        ProcessingStep step = ProcessingStep.builder()
                .sessionId(sessionId)
                .documentId(documentId)
                .attachmentGroupId(attachmentGroupId)
                .stepName(stepName)
                .status(ProcessingStepStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        return repository.save(step);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID stepId) {
        repository.findById(stepId).ifPresent(step -> {
            step.setStatus(ProcessingStepStatus.COMPLETED);
            step.setFinishedAt(LocalDateTime.now());
            repository.save(step);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID stepId, String errorMessage) {
        repository.findById(stepId).ifPresent(step -> {
            step.setStatus(ProcessingStepStatus.FAILED);
            step.setFinishedAt(LocalDateTime.now());
            step.setErrorMessage(errorMessage != null && errorMessage.length() > 1000
                    ? errorMessage.substring(0, 1000) : errorMessage);
            repository.save(step);
        });
    }
}
