package uk.co.mruoc;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.Optional;

@Builder(toBuilder = true)
public record FormDefinition(Integer id, Double version, FormType type, JsonNode schema, FormDefinitionStatus status,
                             ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime publishedAt) {

    public boolean isPublished() {
        return Optional.ofNullable(status).map(FormDefinitionStatus::isPublished).orElse(false);
    }

    public FormDefinition withSchema(JsonNode schema, ZonedDateTime now) {
        return toBuilder().schema(schema).updatedAt(now).build();
    }
}
