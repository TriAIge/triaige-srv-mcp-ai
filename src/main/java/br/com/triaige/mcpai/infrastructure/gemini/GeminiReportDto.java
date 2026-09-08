package br.com.triaige.mcpai.infrastructure.gemini;

import br.com.triaige.mcpai.api.dto.response.RelatorioEstruturado;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Subconjunto do schema da seção 6 que o próprio Gemini produz (ver prompt.txt) —
 * sessionId/protocolo/areaJuridica/tipoCaso/metadados são preenchidos pelo use case a
 * partir de dados já conhecidos, não confiados ao modelo (reduz risco de o modelo "ecoar"
 * um id incorretamente, e é consistente com a regra de não inventar fatos).
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiReportDto {
    private String resumoExecutivo;
    private String fundamentacaoJuridica;
    private List<RelatorioEstruturado.EvidenciaAnalisada> evidenciasAnalisadas;
    private List<RelatorioEstruturado.JurisprudenciaCitada> jurisprudenciaCitada;
    private List<RelatorioEstruturado.RiscoIdentificado> riscosIdentificados;
    private RelatorioEstruturado.Recomendacao recomendacao;

    // Fase 4 (schemaVersion "2.0").
    private RelatorioEstruturado.ClassificacaoInicial classificacaoInicial;
    private RelatorioEstruturado.AvaliacaoCriticidade avaliacaoCriticidade;
    private RelatorioEstruturado.PartesExtraidas partesExtraidas;
    private RelatorioEstruturado.ControleDePrazos controleDePrazos;
    private RelatorioEstruturado.ResumoEstruturado resumoEstruturado;
}
