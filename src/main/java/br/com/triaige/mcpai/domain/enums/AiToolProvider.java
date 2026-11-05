package br.com.triaige.mcpai.domain.enums;

/** Valores de ai_tool_calls.provider (VARCHAR livre) — agnóstico ao provedor real por design (nota de risco). */
public final class AiToolProvider {

    public static final String AWS_TEXTRACT = "aws_textract";
    public static final String INTERNAL_REGEX = "internal_regex";
    public static final String INTERNAL = "internal";
    public static final String INTERNAL_HEURISTIC = "internal_heuristic";
    public static final String MOCKAPI_IO = "mockapi_io";
    public static final String GEMINI = "gemini";

    private AiToolProvider() {
    }
}
