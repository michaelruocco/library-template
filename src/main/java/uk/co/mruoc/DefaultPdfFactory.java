package uk.co.mruoc;

import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
public class DefaultPdfFactory implements PdfFactory {

    private final PdfImage logo;
    private final DateTimeFormatter dateFormatter;
    private final PdfStyles styles;

    public DefaultPdfFactory() {
        this(new PdfImage(), DateTimeFormatter.ofPattern("dd/MM/yyyy"), new PdfStyles());
    }

    @Override
    public void toPdf(RequestForm form, OutputStream outputStream) {
        try {
            generate(form, outputStream);
        } catch (DocumentException e) {
            throw new PdfException("Failed to generate PDF", e);
        }
    }

    private void generate(RequestForm form, OutputStream outputStream) {
        var schema = form.getSchema();
        var data = form.data();
        try (var document = new Document(PageSize.A4)) {
            PdfWriter.getInstance(document, new NonClosingOutputStream(outputStream));
            document.open();
            document.add(logo.load());
            var steps = schema.path("steps");
            renderSteps(document, steps, data);
            displaySignedBy(document, form);
        }
    }

    private void renderSteps(Document document, JsonNode steps, JsonNode data) {
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            var title = step.path("title").asString();
            var schema = step.path("schema");
            var uiSchema = step.path("uiSchema");
            var stepData = toStepData(data, i);
            renderStep(document, title, schema, uiSchema, stepData);
            document.add(Chunk.NEWLINE);
        }
    }

    private JsonNode toStepData(JsonNode data, int i) {
        if (data.isArray() && i < data.size()) {
            return data.get(i);
        }
        return MissingNode.getInstance();
    }

    private void renderStep(Document document, String title, JsonNode schema, JsonNode uiSchema, JsonNode stepData) {
        var heading = styles.toSectionHeading(title);
        heading.setSpacingAfter(2f);
        document.add(heading);
        renderSchemaContents(document, schema, stepData, uiSchema);
    }

    private void renderSchemaContents(Document document, JsonNode schema, JsonNode data, JsonNode uiSchema) {
        var properties = schema.path("properties");
        renderProperties(document, properties, data, uiSchema);
        var ifSchema = schema.path("if");
        if (ifSchema.isMissingNode()) {
            return;
        }

        var matches = matchesSchema(ifSchema, data);
        var branch = matches ? schema.path("then") : schema.path("else");
        if (!branch.isObject()) {
            return;
        }

        renderSchemaContents(document, branch, data, uiSchema);
    }

    private void renderProperties(Document document, JsonNode properties, JsonNode data, JsonNode uiSchema) {
        if (!properties.isObject()) {
            return;
        }

        for (var fieldName : getFieldOrder(properties, uiSchema.path("ui:order"))) {
            renderProperty(document, properties.path(fieldName), data.path(fieldName), uiSchema.path(fieldName));
        }
    }

    private void renderProperty(Document document, JsonNode schema, JsonNode data, JsonNode uiSchema) {
        var type = schema.path("type").asString();
        var widget = uiSchema.path("ui:widget").asString("");

        if ("DeclarationCheckboxWidget".equals(widget)) {
            renderDeclaration(document, data, uiSchema);
            return;
        }

        switch (type) {
            case "array" -> renderArrayProperty(document, schema, data, uiSchema);
            case "object" -> renderObjectProperty(document, schema, data, uiSchema);
            default -> renderScalarProperty(document, schema, data);
        }
    }

    private void renderArrayProperty(Document document, JsonNode schema, JsonNode data, JsonNode uiSchema) {
        addPropertyTitle(document, schema);
        var itemsSchema = schema.path("items");
        var itemType = itemsSchema.path("type").asString();
        if (!"object".equals(itemType)) {
            return;
        }

        var properties = itemsSchema.path("properties");
        var itemUiSchema = uiSchema.path("items");
        var fieldNames = getFieldOrder(properties, itemUiSchema.path("ui:order"));
        if (fieldNames.isEmpty()) {
            return;
        }

        var table = new PdfPTable(fieldNames.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(5f);
        for (var fieldName : fieldNames) {
            var title = properties.path(fieldName).path("title").asString(fieldName);
            table.addCell(toCell(title, styles.headingFont()));
        }

        if (data.isArray()) {
            for (var item : data) {
                for (var fieldName : fieldNames) {
                    var fieldSchema = properties.path(fieldName);
                    var value = item.path(fieldName);
                    var formattedValue = formatValue(fieldSchema, value);
                    table.addCell(toCell(formattedValue, styles.bodyFont()));
                }
            }
        }

        document.add(table);
        document.add(Chunk.NEWLINE);
    }

    private PdfPCell toCell(String value, Font font) {
        var cell = new PdfPCell(new Phrase(value, font));
        cell.setBorderColor(styles.tableBorderColor());
        cell.setBorderWidth(0.25f);
        cell.setPadding(3f);
        cell.setPaddingBottom(6f);
        return cell;
    }

    private void renderObjectProperty(Document document, JsonNode schema, JsonNode data, JsonNode uiSchema) {
        addPropertyTitle(document, schema);
        renderSchemaContents(document, schema, data, uiSchema);
    }

    private void renderScalarProperty(Document document, JsonNode propertySchema, JsonNode value) {
        addPropertyTitle(document, propertySchema);
        var formattedValue = formatValue(propertySchema, value);
        document.add(styles.toBody(formattedValue));
        document.add(Chunk.NEWLINE);
    }

    private void renderDeclaration(Document document, JsonNode data, JsonNode uiSchema)  {
        var options = uiSchema.path("ui:options");
        var points = options.path("points");

        if (points.isArray()) {
            int number = 1;
            for (var point : points) {
                var numberedPoint = number + ". " + point.asString();
                var paragraph = styles.toBody(numberedPoint);
                paragraph.setIndentationLeft(12f);
                paragraph.setFirstLineIndent(-12f);
                paragraph.setSpacingAfter(6f);
                document.add(paragraph);
                number++;
            }
        }

        var summary = options.path("summary");
        if (summary.isString() && !summary.asString().isBlank()) {
            Paragraph summaryParagraph = styles.toBody(summary.asString());
            summaryParagraph.setSpacingAfter(8f);
            document.add(summaryParagraph);
        }

        var heading = styles.toHeading("Declaration accepted");
        heading.setSpacingBefore(10f);
        heading.setSpacingAfter(3f);
        document.add(heading);

        var body = styles.toBody(toBooleanAsString(data, "Not provided"));
        document.add(body);
    }

    private void addPropertyTitle(Document document, JsonNode schema) throws DocumentException {
        var title = schema.path("title").asString();
        if (!title.isBlank()) {
            Paragraph heading = styles.toHeading(title);
            document.add(heading);
        }
    }

    private String formatValue(JsonNode schema, JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        var options = schema.path("oneOf");
        if (options.isArray()) {
            for (var option : options) {
                if (option.has("const") && option.path("const").equals(value)) {
                    return option.path("title").asString(value.asString());
                }
            }
        }

        return toBooleanAsString(value, value.asString());
    }

    private String toBooleanAsString(JsonNode data, String defaultValue) {
        if (data.isBoolean()) {
            return data.asBoolean() ? "Yes" : "No";
        }
        return defaultValue;
    }

    private boolean matchesSchema(JsonNode schema, JsonNode data) {
        if (schema.has("type") && !matchesType(schema.path("type"), data)) {
            return false;
        }

        if (schema.has("const") && !schema.path("const").equals(data)) {
            return false;
        }

        var enumValues = schema.path("enum");

        if (enumValues.isArray()) {
            boolean found = false;

            for (var option : enumValues) {
                if (option.equals(data)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        var required = schema.path("required");
        if (required.isArray()) {
            for (var field : required) {
                if (!data.has(field.asString())) {
                    return false;
                }
            }
        }

        var properties = schema.path("properties");
        if (properties.isObject()) {
            for (var entry : properties.properties()) {
                var name = entry.getKey();
                if (data.has(name) && !matchesSchema(entry.getValue(), data.path(name))) {
                    return false;
                }
            }
        }

        var allOf = schema.path("allOf");
        if (allOf.isArray()) {
            for (var subSchema : allOf) {
                if (!matchesSchema(subSchema, data)) {
                    return false;
                }
            }
        }

        var anyOf = schema.path("anyOf");
        if (anyOf.isArray()) {
            boolean found = false;

            for (var subSchema : anyOf) {
                if (matchesSchema(subSchema, data)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        var oneOf = schema.path("oneOf");
        if (oneOf.isArray()) {
            int matches = 0;

            for (var subSchema : oneOf) {
                if (matchesSchema(subSchema, data)) {
                    matches++;
                }
            }

            if (matches != 1) {
                return false;
            }
        }

        var not = schema.path("not");
        if (not.isObject() && matchesSchema(not, data)) {
            return false;
        }

        return true;
    }

    private boolean matchesType(JsonNode typeSchema, JsonNode value) {
        if (typeSchema.isArray()) {
            for (var type : typeSchema) {
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

    private List<String> getFieldOrder(JsonNode properties, JsonNode uiOrder) {
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

    private void displaySignedBy(Document document, RequestForm form) {
        var heading = styles.toHeading("Signed by Project Developer/Representative Entity");
        heading.setSpacingBefore(10f);
        heading.setSpacingAfter(3f);
        document.add(heading);

        var submitted = form.submittedBy() + " - " + dateFormatter.format(form.submittedAt());
        document.add(styles.toBody(submitted));
    }
}
