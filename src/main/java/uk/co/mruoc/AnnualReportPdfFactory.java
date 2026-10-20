package uk.co.mruoc;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.awt.Color;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AnnualReportPdfFactory implements PdfFactory {

    private static final Color TEAL = new Color(0, 166, 166);
    private static final Color TEXT = new Color(89, 89, 89);

    private static final Font HEADING_FONT =
            new Font(Font.HELVETICA, 12, Font.BOLD, TEAL);

    private static final Font QUESTION_FONT =
            new Font(Font.HELVETICA, 10, Font.BOLD, TEXT);

    private static final Font BODY_FONT =
            new Font(Font.HELVETICA, 10, Font.NORMAL, TEXT);

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

            JsonNode steps = schema.path("steps");

            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                JsonNode stepSchema = step.path("schema");
                JsonNode stepUiSchema = step.path("uiSchema");
                JsonNode stepData = i < data.size()
                        ? data.get(i)
                        : MissingNode.getInstance();

                var heading = new Paragraph(
                        step.path("title").asString(),
                        HEADING_FONT
                );
                heading.setSpacingAfter(5f);
                document.add(heading);

                // Render each property in this step.
                JsonNode properties = stepSchema.path("properties");

                for (String propertyName :
                        getFieldOrder(properties, stepUiSchema.path("ui:order"))) {

                    renderProperty(
                            document,
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

    private void renderProperty(
            Document document,
            JsonNode propertySchema,
            JsonNode propertyData,
            JsonNode propertyUiSchema
    ) throws DocumentException {

        // Display the property's description if present.
        //String description = propertySchema.path("description").asText();
        //if (!description.isBlank()) {
            //document.add(new Paragraph(description));
        //}

        String type = propertySchema.path("type").asString();

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
        if ("object".equals(itemsSchema.path("type").asString())) {

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
                        .asString(fieldName);

                table.addCell(new PdfPCell(new Phrase(title, QUESTION_FONT)));
            }

            // Each array element becomes a row.
            if (arrayData.isArray()) {
                for (JsonNode item : arrayData) {
                    for (String fieldName : fieldNames) {
                        JsonNode value = item.path(fieldName);
                        var formattedValue = formatValue(properties.path(fieldName), value);
                        table.addCell(new PdfPCell(new Phrase(formattedValue, BODY_FONT)));
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

        document.add(new Paragraph(formattedValue, BODY_FONT));
        document.add(Chunk.NEWLINE);
    }

    private void addPropertyTitle(
            Document document,
            JsonNode schema
    ) throws DocumentException {

        String title = schema.path("title").asString();

        if (!title.isBlank()) {
            document.add(new Paragraph(
                    title,
                    QUESTION_FONT
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
                    return option.path("title").asString(value.asString());
                }
            }
        }

        if (value.isBoolean()) {
            return value.asBoolean() ? "Yes" : "No";
        }

        return value.asString();
    }

    private List<String> getFieldOrder(
            JsonNode properties,
            JsonNode uiOrder) {

        List<String> result = new ArrayList<>();

        if (uiOrder.isArray()) {
            for (JsonNode field : uiOrder) {
                String name = field.asString();

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
