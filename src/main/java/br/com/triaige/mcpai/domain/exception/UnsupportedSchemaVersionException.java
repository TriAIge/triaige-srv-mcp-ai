package br.com.triaige.mcpai.domain.exception;

public class UnsupportedSchemaVersionException extends McpException {

    public UnsupportedSchemaVersionException(String schemaVersion) {
        super("UNSUPPORTED_SCHEMA_VERSION", "Versão de schema não suportada: " + schemaVersion);
    }
}
