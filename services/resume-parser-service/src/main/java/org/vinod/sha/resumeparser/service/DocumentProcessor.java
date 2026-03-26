package org.vinod.sha.resumeparser.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentProcessor {

    /**
     * Extract text from file based on format
     */
    public String extractText(byte[] fileContent, String fileFormat) throws IOException {
        switch (fileFormat.toLowerCase()) {
            case "pdf":
                return extractFromPdf(fileContent);
            case "docx":
            case "doc":
                return extractFromDocx(fileContent);
            case "txt":
                return extractFromTxt(fileContent);
            default:
                throw new IllegalArgumentException("Unsupported file format: " + fileFormat);
        }
    }

    private String extractFromPdf(byte[] fileContent) throws IOException {
        // For now, return placeholder text
        // In production, integrate with advanced PDF parsing library
        log.warn("PDF parsing not fully implemented. Returning placeholder text.");
        return "PDF content - full extraction requires additional PDF parsing library";
    }

    private String extractFromDocx(byte[] fileContent) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(fileContent))) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }

    private String extractFromTxt(byte[] fileContent) throws IOException {
        return new String(fileContent, StandardCharsets.UTF_8);
    }

    /**
     * Validate file before processing
     */
    public void validateFile(byte[] fileContent, String fileName, long maxFileSizeBytes) {
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("File content is empty");
        }

        if (fileContent.length > maxFileSizeBytes) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size of %d bytes", maxFileSizeBytes)
            );
        }

        String extension = getFileExtension(fileName).toLowerCase();
        if (!isValidFileFormat(extension)) {
            throw new IllegalArgumentException("Unsupported file format: " + extension);
        }
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    private boolean isValidFileFormat(String extension) {
        return extension.matches("pdf|docx?|txt");
    }
}

