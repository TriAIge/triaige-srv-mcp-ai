package br.com.triaige.mcpai.infrastructure.gemini;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Carrega o template de prompt de sistema em {@code classpath:prompts/analysis/prompt.txt}.
 * Lido uma vez na construção do bean e mantido em memória (arquivo de classpath, imutável
 * em runtime).
 *
 * <p>Usa {@code getClass().getClassLoader()} em vez de {@code ResourceLoader}/
 * {@code Thread.currentThread().getContextClassLoader()} de propósito: este método é
 * chamado de dentro de {@code AnalyzeSessionUseCase}, que roda a análise inteira em uma
 * {@code CompletableFuture.supplyAsync} (timeout da spec seção 5.4) — as threads do
 * {@code ForkJoinPool.commonPool()} não herdam o classloader da aplicação (num fat jar
 * Spring Boot, é um {@code LaunchedURLClassLoader} distinto do classloader "de sistema" que
 * essas threads carregam por padrão), então resolver o recurso pelo classloader que
 * carregou esta própria classe é o que funciona de forma confiável em qualquer thread.
 */
@Component
public class PromptProvider {

    private static final String PATH = "prompts/analysis/prompt.txt";

    private final String prompt = readFromClasspath();

    public String load() {
        return prompt;
    }

    private String readFromClasspath() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IOException("Recurso de classpath não encontrado: " + PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar o prompt: " + PATH, e);
        }
    }
}
