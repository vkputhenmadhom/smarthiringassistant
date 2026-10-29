package org.vinod.sha.matcher.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/** Stores a skill list in one TEXT column, one skill per line (skill names can contain commas, not newlines). */
@Converter
public class SkillListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> skills) {
        return skills == null || skills.isEmpty() ? null : String.join("\n", skills);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        return column == null || column.isBlank() ? List.of() : Arrays.stream(column.split("\n")).filter(s -> !s.isBlank()).toList();
    }
}
