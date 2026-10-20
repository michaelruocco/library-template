package uk.co.mruoc;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.co.mruoc.file.FileLoader;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;


public class AnnualReportPdfFactoryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PdfFactory factory = new AnnualReportPdfFactory();

    @Test
    void shouldCreatePdf() throws IOException {
        var outputPath = Path.of("output", "annual-report.pdf");
        Files.createDirectories(outputPath.getParent());
        var form = buildForm();
        try (var fileStream = Files.newOutputStream(outputPath);
             var outputStream = new TrackingOutputStream(fileStream)) {
            factory.toPdf(form, outputStream);
            assertThat(outputStream.isClosed()).isFalse();
        }
        System.out.println("PDF written to: " + outputPath);
    }

    private static RequestForm buildForm() {
        var now = now();
        return RequestForm.builder()
                .id(1)
                .requestId(2)
                .createdAt(now.minusDays(2))
                .data(toJsonNode(FileLoader.loadContentFromClasspath("annual-report-form-data.json")))
                .definition(buildDefinition())
                .status(RequestFormStatus.SUBMITTED)
                .updatedAt(now)
                .submittedAt(now)
                .submittedBy("Michael Ruocco")
                .build();
    }

    private static FormDefinition buildDefinition() {
        var now = now();
        return FormDefinition.builder()
                .id(3)
                .version(1.0)
                .type(FormType.ANNUAL_REPORT)
                .status(FormDefinitionStatus.PUBLISHED)
                .createdAt(now.minusDays(12))
                .updatedAt(now.minusDays(11))
                .publishedAt(now.minusDays(10))
                .schema(toJsonNode(FileLoader.loadContentFromClasspath("annual-report-form-schema.json")))
                .build();
    }

    private static JsonNode toJsonNode(String content) {
        return MAPPER.readTree(content);
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.of(2026, 10, 20, 21, 34, 5, 1, ZoneOffset.UTC);
    }

    private static class TrackingOutputStream extends FilterOutputStream {

        private boolean closed;

        TrackingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
    }
}
