package br.com.triaige.mcpai.application.service;

import java.util.UUID;

/** Uma parte (documento) bem-sucedida de um attachment_group_id, pronta para T3. */
public record GroupPart(UUID documentId, int partNumber, String anonymizedText, int pages,
                         String nomeArquivoOriginal, String tipoDocumento) {
}
