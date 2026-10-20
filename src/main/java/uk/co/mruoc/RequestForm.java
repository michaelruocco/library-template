package uk.co.mruoc;

import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.Optional;

@Builder(toBuilder = true)
public record RequestForm(Integer id, Integer requestId, FormDefinition definition, JsonNode data,
                          RequestFormStatus status, ZonedDateTime createdAt, ZonedDateTime updatedAt,
                          ZonedDateTime submittedAt, String submittedBy) {

    public boolean isSubmitted() {
        return Optional.ofNullable(status).map(RequestFormStatus::isSubmitted).orElse(false);
    }

    public RequestForm withData(JsonNode data, ZonedDateTime now) {
        return toBuilder().data(data).updatedAt(now).build();
    }

    public double getVersion() {
        return definition.version();
    }

    public FormType getType() {
        return definition.type();
    }

    public JsonNode getSchema() {
        return definition.schema();
    }
}


