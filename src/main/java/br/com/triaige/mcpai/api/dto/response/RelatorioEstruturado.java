package br.com.triaige.mcpai.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Schema do relatório estruturado. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelatorioEstruturado {

    @Builder.Default
    private String schemaVersion = "2.0";

    private UUID sessionId;
    private String protocolo;
    private String areaJuridica;
    private String tipoCaso;
    private String resumoExecutivo;
    private String fundamentacaoJuridica;
    private List<EvidenciaAnalisada> evidenciasAnalisadas;
    private List<JurisprudenciaCitada> jurisprudenciaCitada;
    private List<RiscoIdentificado> riscosIdentificados;
    private Recomendacao recomendacao;
    private Metadados metadados;

    // Campos (schemaVersion "2.0").
    private ClassificacaoInicial classificacaoInicial;
    private AvaliacaoCriticidade avaliacaoCriticidade;
    private PartesExtraidas partesExtraidas;
    private ControleDePrazos controleDePrazos;
    private ResumoEstruturado resumoEstruturado;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenciaAnalisada {
        private UUID attachmentGroupId;
        private String tipoDocumento;
        /** alta | media | baixa */
        private String relevancia;
        private String observacao;
        /**
         * Preenchido pelo use case a partir de {@code TrustedDocumentPayload},
         * nunca pedido ao modelo (dado já em mãos, sem custo de LLM).
         */
        private String nomeArquivoOriginal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JurisprudenciaCitada {
        private String titulo;
        private String ementa;
        private String fonte;
        private String url;
        /** alta | media | baixa */
        private String aderencia;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiscoIdentificado {
        private String descricao;
        /** alta | media | baixa */
        private String severidade;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recomendacao {
        /** viavel | inviavel | necessita_mais_documentos */
        private String classificacao;
        private String justificativa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadados {
        private String modeloUtilizado;
        private int toolCallsUsados;
        private java.time.LocalDateTime geradoEm;
        private long tempoProcessamentoMs;
    }

    // ---- (schemaVersion "2.0") ----

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassificacaoInicial {
        /** string livre, ou o literal "não identificado nas evidências" */
        private String foroCompetenteEstimado;
        /** junior | pleno | socio */
        private String nivelAtendimentoSugerido;
        /** sugestão textual indicativa — não é atribuição real (não há tabela de equipes) */
        private String equipeResponsavelSugerida;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvaliacaoCriticidade {
        private Nivel criticidade;
        private Nivel complexidade;
        private ImpactoFinanceiroEstimado impactoFinanceiroEstimado;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Nivel {
        /** alta | media | baixa */
        private String nivel;
        /** inteiro 0-100 */
        private Integer confianca;
        private String justificativa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactoFinanceiroEstimado {
        /** string livre (nunca um tipo numérico — pode carregar "não identificado nas evidências") */
        private String valor;
        /** inteiro 0-100 */
        private Integer confianca;
        private String justificativa;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartesExtraidas {
        private String poloAtivo;
        private String poloPassivo;
        private String terceirosInteressados;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControleDePrazos {
        /** data (ISO8601 ou descrição textual), ou o literal "não identificado nas evidências" */
        private String dataFato;
        private String dataIntimacao;
        private String prazoFatalEstimado;
        private String tipoPrazo;
        /** baixo | alerta_iminente | não_identificado */
        private String riscoPrescricao;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumoEstruturado {
        private String sinteseFatos;
        private List<String> pedidos;
        private List<String> pontosCriticos;
    }
}
