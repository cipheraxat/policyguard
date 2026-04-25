package com.policyguard.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfExtractionServiceTest {

    private final PdfExtractionService service = new PdfExtractionService();

    @Test
    void extractsTextFromMinimalPdf() throws IOException {
        byte[] pdf = buildPdf("Hello world");

        String extracted = service.extract(pdf);

        assertThat(extracted).contains("Hello world");
    }

    @Test
    void extractsMultiLineText() throws IOException {
        byte[] pdf = buildPdf("Line one");

        String extracted = service.extract(pdf);

        assertThat(extracted).contains("Line one");
    }

    @Test
    void extractsTextFromMultiPagePdf() throws IOException {
        byte[] pdf = buildMultiPagePdf("Page1Text", "Page2Text");

        String extracted = service.extract(pdf);

        assertThat(extracted).contains("Page1Text");
        assertThat(extracted).contains("Page2Text");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static byte[] buildPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] buildMultiPagePdf(String page1Text, String page2Text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (String text : new String[]{page1Text, page2Text}) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
