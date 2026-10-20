
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

    private static final Font SECTION_FONT =
            new Font(Font.HELVETICA, 12, Font.BOLD, TEAL);

    private static final Font HEADING_FONT =
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
            PdfWriter.getInstance(
                    document,
                    new NonClosingOutputStream(outputStream)
            );

            document.open();

            JsonNode steps = schema.path("steps");

            for (int i = 0; i < steps.size(); i++) {
                JsonNode step = steps.get(i);
                JsonNode stepSchema = step.path("schema");
                JsonNode stepUiSchema = step.path("uiSchema");

                JsonNode stepData = data.isArray() && i < data.size()
                        ? data.get(i)
                        : MissingNode.getInstance();

                Paragraph heading = new Paragraph(
                        step.path("title").asString(),
                        SECTION_FONT
                );
                heading.setSpacingAfter(5f);
                document.add(heading);

                // Render ordinary and conditional properties.
                renderSchemaContents(
                        document,
                        stepSchema,
                        stepData,
                        stepUiSchema
                );

                document.add(Chunk.NEWLINE);
            }
        }
    }

    /**
     * Renders ordinary properties, then any matching if/then/else branch.
     * This can be used for both the root step schema and nested object schemas.
     */
    private void renderSchemaContents(
            Document document,
            JsonNode schema,
            JsonNode data,
            JsonNode uiSchema
    ) throws DocumentException {

        renderProperties(
                document,
                schema.path("properties"),
                data,
                uiSchema
        );

        JsonNode ifSchema = schema.path("if");

        if (!ifSchema.isMissingNode()) {
            boolean matches = matchesSchema(ifSchema, data);

            JsonNode branch = matches
                    ? schema.path("then")
                    : schema.path("else");

            if (branch.isObject()) {
                renderSchemaContents(
                        document,
                        branch,
                        data,
                        uiSchema
                );
            }
        }
    }

    /**
     * Renders properties in ui:order, followed by any remaining properties.
     */
    private void renderProperties(
            Document document,
            JsonNode properties,
            JsonNode data,
            JsonNode uiSchema
    ) throws DocumentException {

        if (!properties.isObject()) {
            return;
        }

        for (String fieldName : getFieldOrder(
                properties,
                uiSchema.path("ui:order"))) {

            renderProperty(
                    document,
                    properties.path(fieldName),
                    data.path(fieldName),
                    uiSchema.path(fieldName)
            );
        }
    }

    private void renderProperty(
            Document document,
            JsonNode propertySchema,
            JsonNode propertyData,
            JsonNode propertyUiSchema
    ) throws DocumentException {

        String type = propertySchema.path("type").asString();

        switch (type) {
            case "array" -> renderArrayProperty(
                    document,
                    propertySchema,
                    propertyData,
                    propertyUiSchema
            );

            case "object" -> renderObjectProperty(
                    document,
                    propertySchema,
                    propertyData,
                    propertyUiSchema
            );

            default -> renderScalarProperty(
                    document,
                    propertySchema,
                    propertyData
            );
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

            // Column headings.
            for (String fieldName : fieldNames) {
                String title = properties.path(fieldName)
                        .path("title")
                        .asString(fieldName);

                table.addCell(new PdfPCell(
                        new Phrase(title, HEADING_FONT)
                ));
            }

            // Each array element becomes a row.
            if (arrayData.isArray()) {
                for (JsonNode item : arrayData) {
                    for (String fieldName : fieldNames) {
                        JsonNode fieldSchema = properties.path(fieldName);
                        JsonNode value = item.path(fieldName);

                        table.addCell(new PdfPCell(
                                new Phrase(
                                        formatValue(fieldSchema, value),
                                        BODY_FONT
                                )
                        ));
                    }
                }
            }

            document.add(table);
            document.add(Chunk.NEWLINE);
        }
    }

    private void renderObjectProperty(
            Document document,
            JsonNode objectSchema,
            JsonNode objectData,
            JsonNode objectUiSchema
    ) throws DocumentException {

        addPropertyTitle(document, objectSchema);

        // Also evaluates conditionals nested inside this object.
        renderSchemaContents(
                document,
                objectSchema,
                objectData,
                objectUiSchema
        );
    }

    private void renderScalarProperty(
            Document document,
            JsonNode propertySchema,
            JsonNode value
    ) throws DocumentException {

        addPropertyTitle(document, propertySchema);

        document.add(new Paragraph(
                formatValue(propertySchema, value),
                BODY_FONT
        ));

        document.add(Chunk.NEWLINE);
    }

    private void addPropertyTitle(
            Document document,
            JsonNode schema
    ) throws DocumentException {

        String title = schema.path("title").asString();

        if (!title.isBlank()) {
            Paragraph heading = new Paragraph(title, HEADING_FONT);
            heading.setSpacingBefore(3f);
            heading.setSpacingAfter(2f);
            document.add(heading);
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

    /**
     * Evaluates the common JSON Schema keywords needed for conditional
     * rendering: type, const, enum, required, properties, allOf, anyOf,
     * oneOf and not.
     *
     * This is a subset of JSON Schema validation, not a full implementation.
     */
    private boolean matchesSchema(JsonNode schema, JsonNode data) {

        if (schema.has("type")
                && !matchesType(schema.path("type"), data)) {
            return false;
        }

        if (schema.has("const")
                && !schema.path("const").equals(data)) {
            return false;
        }

        JsonNode enumValues = schema.path("enum");

        if (enumValues.isArray()) {
            boolean found = false;

            for (JsonNode option : enumValues) {
                if (option.equals(data)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        // Every field in "required" must exist on the object.
        JsonNode required = schema.path("required");

        if (required.isArray()) {
            for (JsonNode field : required) {
                if (!data.has(field.asString())) {
                    return false;
                }
            }
        }

        // Every declared property schema must match if that property exists.
        // As specified by JSON Schema, absent properties are not a mismatch.
        JsonNode properties = schema.path("properties");

        if (properties.isObject()) {
            for (var entry : properties.properties()) {
                String name = entry.getKey();

                if (data.has(name)
                        && !matchesSchema(
                        entry.getValue(),
                        data.path(name))) {
                    return false;
                }
            }
        }

        JsonNode allOf = schema.path("allOf");

        if (allOf.isArray()) {
            for (JsonNode subSchema : allOf) {
                if (!matchesSchema(subSchema, data)) {
                    return false;
                }
            }
        }

        JsonNode anyOf = schema.path("anyOf");

        if (anyOf.isArray()) {
            boolean found = false;

            for (JsonNode subSchema : anyOf) {
                if (matchesSchema(subSchema, data)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        JsonNode oneOf = schema.path("oneOf");

        if (oneOf.isArray()) {
            int matches = 0;

            for (JsonNode subSchema : oneOf) {
                if (matchesSchema(subSchema, data)) {
                    matches++;
                }
            }

            if (matches != 1) {
                return false;
            }
        }

        JsonNode not = schema.path("not");

        if (not.isObject() && matchesSchema(not, data)) {
            return false;
        }

        return true;
    }

    private boolean matchesType(JsonNode typeSchema, JsonNode value) {

        if (typeSchema.isArray()) {
            for (JsonNode type : typeSchema) {
                if (matchesType(type, value)) {
                    return true;
                }
            }
            return false;
        }

        return switch (typeSchema.asString()) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isString();
            case "boolean" -> value.isBoolean();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "null" -> value.isNull();
            default -> true;
        };
    }

    private List<String> getFieldOrder(
            JsonNode properties,
            JsonNode uiOrder
    ) {

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