package br.com.triaige.mcpai.domain.converter;

import br.com.triaige.mcpai.domain.enums.AiToolName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class AiToolNameConverter implements AttributeConverter<AiToolName, String> {

    @Override
    public String convertToDatabaseColumn(AiToolName attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public AiToolName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AiToolName.fromValue(dbData);
    }
}
