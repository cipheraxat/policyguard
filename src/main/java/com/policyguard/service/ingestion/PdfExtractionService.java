package com.policyguard.service.ingestion;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/**
 * Extracts plain text from PDF bytes using Apache PDFBox 3.
 */
@Service
public class PdfExtractionService {

    /**
     * Extract all text from the given PDF bytes.
     *
     * @param pdf raw PDF file bytes
     * @return extracted plain text
     * @throws IllegalArgumentException if the bytes cannot be parsed as a PDF
     */
    public String extract(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to extract text from PDF", e);
        }
    }
}
