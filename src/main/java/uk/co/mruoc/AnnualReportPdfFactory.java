package uk.co.mruoc;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AnnualReportPdfFactory implements PdfFactory {

    @Override
    public void toPdf(RequestForm form, OutputStream outputStream) {
        try {
            generate(form.getSchema(), form.data(), outputStream);
        } catch (DocumentException e) {
            throw new PdfException("Failed to generate PDF", e);
        }
    }

    private void generate(
            JsonNode schema,
            JsonNode data,
            OutputStream outputStream
    ) throws DocumentException {

        try (Document document = new Document(PageSize.A4)) {
            PdfWriter.getInstance(document, new NonClosingOutputStream(outputStream));
            document.open();
            document.add(title());

            JsonNode steps = schema.path("steps");

            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                JsonNode stepSchema = step.path("schema");
                JsonNode stepUiSchema = step.path("uiSchema");
                JsonNode stepData = i < data.size()
                        ? data.get(i)
                        : MissingNode.getInstance();

                // Add the step heading.
                document.add(new Paragraph(
                        step.path("title").asText(),
                        new Font(Font.HELVETICA, 16, Font.BOLD)
                ));

                // Render each property in this step.
                JsonNode properties = stepSchema.path("properties");

                for (String propertyName :
                        getFieldOrder(properties, stepUiSchema.path("ui:order"))) {

                    renderProperty(
                            document,
                            propertyName,
                            properties.path(propertyName),
                            stepData.path(propertyName),
                            stepUiSchema.path(propertyName)
                    );
                }

                // Space between sections.
                document.add(Chunk.NEWLINE);
            }
        }
    }

    private Paragraph title() {
        var title = new Paragraph("Annual Report Form");
        title.setAlignment(Element.ALIGN_CENTER);
        return title;
    }

    private void renderProperty(
            Document document,
            String propertyName,
            JsonNode propertySchema,
            JsonNode propertyData,
            JsonNode propertyUiSchema
    ) throws DocumentException {

        // Display the property's description if present.
        String description = propertySchema.path("description").asText();

        if (!description.isBlank()) {
            document.add(new Paragraph(description));
        }

        String type = propertySchema.path("type").asText();

        switch (type) {
            case "array" -> renderArrayProperty(
                    document, propertySchema, propertyData, propertyUiSchema);

            case "object" -> renderObjectProperty(
                    document, propertySchema, propertyData, propertyUiSchema);

            default -> renderScalarProperty(
                    document, propertySchema, propertyData);
        }
    }

    private void renderArrayProperty(
            Document document,
            JsonNode arraySchema,
            JsonNode arrayData,
            JsonNode arrayUiSchema
    ) throws DocumentException {

        addPropertyTitle(document, arraySchema);

        JsonNode itemsSchema = arraySchema.path("items");

        // An array of objects becomes a table.
        if ("object".equals(itemsSchema.path("type").asText())) {

            JsonNode properties = itemsSchema.path("properties");
            JsonNode itemUiSchema = arrayUiSchema.path("items");

            List<String> fieldNames = getFieldOrder(
                    properties,
                    itemUiSchema.path("ui:order")
            );

            if (fieldNames.isEmpty()) {
                return;
            }

            PdfPTable table = new PdfPTable(fieldNames.size());
            table.setWidthPercentage(100);

            // Column headings come from the item schema.
            for (String fieldName : fieldNames) {
                String title = properties.path(fieldName)
                        .path("title")
                        .asText(fieldName);

                table.addCell(new PdfPCell(new Phrase(title)));
            }

            // Each array element becomes a row.
            if (arrayData.isArray()) {
                for (JsonNode item : arrayData) {
                    for (String fieldName : fieldNames) {
                        JsonNode value = item.path(fieldName);
                        table.addCell(new PdfPCell(
                                new Phrase(formatValue(
                                        properties.path(fieldName), value))
                        ));
                    }
                }
            }

            document.add(table);
        }
    }

    private void renderObjectProperty(
            Document document,
            JsonNode objectSchema,
            JsonNode objectData,
            JsonNode objectUiSchema
    ) throws DocumentException {

        addPropertyTitle(document, objectSchema);

        JsonNode properties = objectSchema.path("properties");

        for (String fieldName : getFieldOrder(
                properties,
                objectUiSchema.path("ui:order"))) {

            renderProperty(
                    document,
                    fieldName,
                    properties.path(fieldName),
                    objectData.path(fieldName),
                    objectUiSchema.path(fieldName)
            );
        }
    }

    private void renderScalarProperty(
            Document document,
            JsonNode propertySchema,
            JsonNode value
    ) throws DocumentException {

        addPropertyTitle(document, propertySchema);

        String formattedValue = formatValue(propertySchema, value);

        document.add(new Paragraph(formattedValue));
        document.add(Chunk.NEWLINE);
    }

    private void addPropertyTitle(
            Document document,
            JsonNode schema
    ) throws DocumentException {

        String title = schema.path("title").asText();

        if (!title.isBlank()) {
            document.add(new Paragraph(
                    title,
                    new Font(Font.HELVETICA, 11, Font.BOLD)
            ));
        }
    }

    private String formatValue(JsonNode schema, JsonNode value) {

        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }

        // Translate values such as true/false into schema-defined labels.
        JsonNode options = schema.path("oneOf");

        if (options.isArray()) {
            for (JsonNode option : options) {
                if (option.has("const")
                        && option.path("const").equals(value)) {
                    return option.path("title").asText(value.asText());
                }
            }
        }

        if (value.isBoolean()) {
            return value.asBoolean() ? "Yes" : "No";
        }

        return value.asText();
    }

    private List<String> getFieldOrder(
            JsonNode properties,
            JsonNode uiOrder) {

        List<String> result = new ArrayList<>();

        if (uiOrder.isArray()) {
            for (JsonNode field : uiOrder) {
                String name = field.asText();

                if (properties.has(name) && !result.contains(name)) {
                    result.add(name);
                }
            }
        }

        properties.properties().forEach(entry -> {
            String name = entry.getKey();
            if (!result.contains(name)) {
                result.add(name);
            }
        });

        return result;
    }
}
