package br.com.triaige.mcpai.infrastructure.sqs.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Mensagem de triaige-mcp-ocr-retry (spec seção 5.2). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrRetryMessage {

    private UUID documentId;
    private UUID sessionId;
    private UUID attachmentGroupId;
    private String rawBucket;
    private String rawObjectKey;
    private int attemptNumber;
}
