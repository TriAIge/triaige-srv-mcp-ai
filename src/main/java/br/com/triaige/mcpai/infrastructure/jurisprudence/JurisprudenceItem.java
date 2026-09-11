package br.com.triaige.mcpai.infrastructure.jurisprudence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Um item de jurisprudência, tanto na resposta de mockapi.io quanto no output de T5. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class JurisprudenceItem {
    private String titulo;
    private String ementa;
    private String fonte;
    private String url;
}
