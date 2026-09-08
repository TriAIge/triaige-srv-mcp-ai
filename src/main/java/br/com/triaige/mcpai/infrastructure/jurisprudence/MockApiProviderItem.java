package br.com.triaige.mcpai.infrastructure.jurisprudence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Formato real de item devolvido por {@code https://6a2ecdd9c9776ca6c0c4f537.mockapi.io/jurisprudence-mock},
 * distinto do contrato interno {@link JurisprudenceItem} (que também é o formato exposto ao
 * prompt do Gemini, spec seção 6). Mapeado para {@link JurisprudenceItem} em
 * {@link MockApiJurisprudenceClient}.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MockApiProviderItem {
    private String title;
    private String court;
    private String caseNumber;
    private String judgmentDate;
    private String summary;
    private String url;
    private String legalArea;
}
