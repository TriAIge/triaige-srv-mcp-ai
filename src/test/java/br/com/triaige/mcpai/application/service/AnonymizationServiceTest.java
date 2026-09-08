package br.com.triaige.mcpai.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Suite obrigatória de T2 (spec seção 5.3, Definition of Done): todas as categorias em
 * formatos variados, falsos positivos conhecidos, e texto sem PII inalterado.
 */
class AnonymizationServiceTest {

    private final AnonymizationService service = new AnonymizationService();

    @Nested
    @DisplayName("CPF")
    class Cpf {

        @ParameterizedTest
        @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
        void redactsValidCpfInAnyFormat(String cpf) {
            AnonymizationResult result = service.anonymize("Requerente CPF " + cpf + " compareceu.");
            assertThat(result.anonymizedText()).contains("[CPF]").doesNotContain(cpf);
            assertThat(result.countsByCategory()).containsEntry("CPF", 1);
        }

        @Test
        @DisplayName("não redige número no formato de CPF mas com dígito verificador inválido (falso positivo conhecido)")
        void doesNotRedactInvalidCheckDigitCpf() {
            String text = "Pedido protocolado sob o número 123.456.789-00 no sistema.";
            AnonymizationResult result = service.anonymize(text);
            assertThat(result.anonymizedText()).isEqualTo(text);
            assertThat(result.countsByCategory()).doesNotContainKey("CPF");
        }

        @Test
        @DisplayName("não redige sequência de dígitos repetidos (ex: 111.111.111-11), CPF inválido por definição")
        void doesNotRedactRepeatedDigitsCpf() {
            String text = "Código de referência: 111.111.111-11";
            AnonymizationResult result = service.anonymize(text);
            assertThat(result.anonymizedText()).isEqualTo(text);
        }
    }

    @Nested
    @DisplayName("CNPJ")
    class Cnpj {

        @ParameterizedTest
        @ValueSource(strings = {"11.789.766/0001-13", "11789766000113"})
        void redactsValidCnpjInAnyFormat(String cnpj) {
            AnonymizationResult result = service.anonymize("Empresa CNPJ " + cnpj + " ré na ação.");
            assertThat(result.anonymizedText()).contains("[CNPJ]").doesNotContain(cnpj);
            assertThat(result.countsByCategory()).containsEntry("CNPJ", 1);
        }

        @Test
        void doesNotRedactInvalidCheckDigitCnpj() {
            String text = "Referência interna 11.222.333/0001-00 não é um CNPJ válido.";
            AnonymizationResult result = service.anonymize(text);
            assertThat(result.anonymizedText()).isEqualTo(text);
        }
    }

    @Nested
    @DisplayName("E-mail")
    class Email {

        @ParameterizedTest
        @ValueSource(strings = {"fulano@example.com", "fulano.silva+caso@dominio.com.br"})
        void redactsEmail(String email) {
            AnonymizationResult result = service.anonymize("Contato: " + email + ".");
            assertThat(result.anonymizedText()).contains("[EMAIL]").doesNotContain(email);
            assertThat(result.countsByCategory()).containsEntry("EMAIL", 1);
        }
    }

    @Nested
    @DisplayName("Telefone")
    class Telefone {

        @ParameterizedTest
        @ValueSource(strings = {
                "(11) 91234-5678",
                "11 91234-5678",
                "91234-5678",
                "+55 11 91234-5678",
                "1234-5678"
        })
        void redactsPhoneWithOrWithoutDddAndCountryCode(String phone) {
            AnonymizationResult result = service.anonymize("Telefone para contato: " + phone + ".");
            assertThat(result.anonymizedText()).contains("[TELEFONE]").doesNotContain(phone);
        }

        @Test
        @DisplayName("não confunde data (dd-mm-yyyy) com telefone")
        void doesNotRedactDateAsPhone() {
            String text = "Audiência marcada para 01-02-2024 às 10h.";
            AnonymizationResult result = service.anonymize(text);
            assertThat(result.anonymizedText()).isEqualTo(text);
            assertThat(result.countsByCategory()).doesNotContainKey("TELEFONE");
        }
    }

    @Nested
    @DisplayName("CEP")
    class Cep {

        @Test
        void redactsCepWithHyphen() {
            AnonymizationResult result = service.anonymize("Endereço no CEP 01310-100 conforme comprovante.");
            assertThat(result.anonymizedText()).contains("[CEP]").doesNotContain("01310-100");
            assertThat(result.countsByCategory()).containsEntry("CEP", 1);
        }
    }

    @Nested
    @DisplayName("RG")
    class Rg {

        @ParameterizedTest
        @ValueSource(strings = {"RG 12.345.678-9", "RG: 12345678-9", "identidade 12.345.678-9"})
        void redactsRgWhenPrecededByContextKeyword(String snippet) {
            AnonymizationResult result = service.anonymize("Portador do " + snippet + ", vem requerer.");
            assertThat(result.anonymizedText()).contains("[RG]");
            assertThat(result.countsByCategory()).containsEntry("RG", 1);
        }
    }

    @Nested
    @DisplayName("Endereço")
    class Endereco {

        @ParameterizedTest
        @ValueSource(strings = {
                "Rua das Flores, 123",
                "Av. Paulista 1000",
                "Avenida Brasil, nº 500"
        })
        void redactsStreetAddress(String address) {
            AnonymizationResult result = service.anonymize("Residente na " + address + ", conforme comprovante.");
            assertThat(result.anonymizedText()).contains("[ENDERECO]");
            assertThat(result.countsByCategory()).containsEntry("ENDERECO", 1);
        }
    }

    @Nested
    @DisplayName("Nome completo")
    class Nome {

        @Test
        void redactsFullName() {
            AnonymizationResult result = service.anonymize("O requerente João da Silva Santos alega dano moral.");
            assertThat(result.anonymizedText()).contains("[NOME]").doesNotContain("João da Silva Santos");
        }

        @Test
        @DisplayName("não redige termos jurídicos/institucionais conhecidos em Title Case (falso positivo conhecido)")
        void doesNotRedactKnownInstitutionalTerms() {
            String text = "Processo distribuído para a Vara Cível conforme decisão do Poder Judiciario.";
            AnonymizationResult result = service.anonymize(text);
            assertThat(result.anonymizedText()).doesNotContain("[NOME]");
        }
    }

    @Test
    @DisplayName("texto sem nenhuma PII não é alterado")
    void doesNotModifyTextWithoutPii() {
        String text = "O contrato prevê multa de 2% em caso de atraso na entrega do imóvel, conforme cláusula 5.";
        AnonymizationResult result = service.anonymize(text);
        assertThat(result.anonymizedText()).isEqualTo(text);
        assertThat(result.countsByCategory()).isEmpty();
    }

    @Test
    @DisplayName("texto nulo/vazio é retornado sem erro")
    void handlesNullAndBlankGracefully() {
        assertThat(service.anonymize(null).anonymizedText()).isNull();
        assertThat(service.anonymize("").anonymizedText()).isEmpty();
    }

    @Test
    @DisplayName("múltiplas categorias no mesmo texto são todas redigidas e contadas")
    void redactsMultipleCategoriesInSameText() {
        String text = "Nome: Maria Aparecida Souza, CPF 529.982.247-25, e-mail maria@example.com, "
                + "telefone (11) 91234-5678, residente na Rua das Palmeiras, 45, CEP 01310-100.";
        AnonymizationResult result = service.anonymize(text);

        assertThat(result.anonymizedText())
                .contains("[NOME]", "[CPF]", "[EMAIL]", "[TELEFONE]", "[ENDERECO]", "[CEP]")
                .doesNotContain("529.982.247-25", "maria@example.com");
        assertThat(result.countsByCategory().values()).allMatch(count -> count >= 1);
    }
}
