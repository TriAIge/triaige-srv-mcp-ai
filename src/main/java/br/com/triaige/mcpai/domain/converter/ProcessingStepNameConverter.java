package br.com.triaige.mcpai.domain.converter;

import br.com.triaige.mcpai.domain.enums.ProcessingStepName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProcessingStepNameConverter implements AttributeConverter<ProcessingStepName, String> {

    @Override
    public String convertToDatabaseColumn(ProcessingStepName attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public ProcessingStepName convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProcessingStepName.fromValue(dbData);
    }
}
