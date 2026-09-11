package br.com.triaige.mcpai.infrastructure.mcp;

import br.com.triaige.mcpai.infrastructure.jurisprudence.JurisprudenceQueryTool;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registra as tools MCP expostas por este servidor (apenas T5). */
@Configuration
@RequiredArgsConstructor
public class McpToolConfig {

    private final JurisprudenceQueryTool jurisprudenceQueryTool;

    @Bean
    public ToolCallbackProvider mcpTools() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(jurisprudenceQueryTool)
                .build();
    }
}
