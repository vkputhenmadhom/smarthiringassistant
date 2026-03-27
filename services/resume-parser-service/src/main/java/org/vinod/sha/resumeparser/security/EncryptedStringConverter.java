package org.vinod.sha.resumeparser.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return SensitiveDataEncryptionService.getInstance().encryptString(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return SensitiveDataEncryptionService.getInstance().decryptString(dbData);
    }
}

