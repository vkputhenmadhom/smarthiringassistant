package org.vinod.sha.resumeparser.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedByteArrayConverter implements AttributeConverter<byte[], byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(byte[] attribute) {
        return SensitiveDataEncryptionService.getInstance().encryptBytes(attribute);
    }

    @Override
    public byte[] convertToEntityAttribute(byte[] dbData) {
        return SensitiveDataEncryptionService.getInstance().decryptBytes(dbData);
    }
}

