package br.com.triaige.mcpai.infrastructure.jurisprudence;

import br.com.triaige.mcpai.domain.exception.JurisprudenceProviderException;
import br.com.triaige.mcpai.infrastructure.config.McpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/**
 * Cliente HTTP para a API mock de jurisprudência — timeout 5s, 1 retry em
 * timeout/5xx. O provedor real ({@code https://6a2ecdd9c9776ca6c0c4f537.mockapi.io/jurisprudence-mock})
 * devolve itens no formato {@link MockApiProviderItem} ({@code title/court/caseNumber/summary/url/legalArea}),
 * mapeado aqui para o contrato interno {@link JurisprudenceItem} ({@code titulo/ementa/fonte/url})
 * usado no prompt do Gemini (campo {@code provider} em ai_tool_calls permite trocar de fonte
 * sem novo desenho de schema).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockApiJurisprudenceClient {

    private final McpProperties mcpProperties;

    public List<JurisprudenceItem> query(String teseJuridica, String areaJuridica) {
        McpProperties.Jurisprudence config = mcpProperties.getJurisprudence();
        RestClient restClient = buildClient(config);

        try {
            return attempt(restClient, teseJuridica, areaJuridica);
        } catch (Exception first) {
            log.warn("Jurisprudence provider call failed, retrying once: {}", first.getMessage());
            try {
                return attempt(restClient, teseJuridica, areaJuridica);
            } catch (Exception second) {
                throw new JurisprudenceProviderException("Falha ao consultar provedor de jurisprudência após retry",
                        second);
            }
        }
    }

    private List<JurisprudenceItem> attempt(RestClient restClient, String teseJuridica, String areaJuridica) {
        List<MockApiProviderItem> result = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("legalArea", areaJuridica == null ? null : areaJuridica.toUpperCase())
                        .build())
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<List<MockApiProviderItem>>() {
                });
        return result == null ? List.of() : result.stream().map(this::toJurisprudenceItem).toList();
    }

    private JurisprudenceItem toJurisprudenceItem(MockApiProviderItem item) {
        String fonte = item.getCourt() == null ? item.getCaseNumber()
                : item.getCaseNumber() == null ? item.getCourt()
                : item.getCourt() + " - " + item.getCaseNumber();
        return JurisprudenceItem.builder()
                .titulo(item.getTitle())
                .ementa(item.getSummary())
                .fonte(fonte)
                .url(item.getUrl())
                .build();
    }

    private RestClient buildClient(McpProperties.Jurisprudence config) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(config.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(config.getTimeoutMs()));

        return RestClient.builder()
                .baseUrl(config.getEndpoint())
                .requestFactory(factory)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw new IllegalStateException("Provedor de jurisprudência respondeu " + response.getStatusCode());
                })
                .build();
    }
}
