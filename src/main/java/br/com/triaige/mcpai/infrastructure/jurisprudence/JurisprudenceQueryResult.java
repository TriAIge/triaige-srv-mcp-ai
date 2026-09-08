package br.com.triaige.mcpai.infrastructure.jurisprudence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Output schema de T5 (spec seção 7) — retorno serializado automaticamente pela tool MCP. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JurisprudenceQueryResult {
    private List<JurisprudenceItem> resultados;
    private boolean cacheHit;
}
