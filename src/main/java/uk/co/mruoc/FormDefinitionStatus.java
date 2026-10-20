package uk.co.mruoc;

public enum FormDefinitionStatus {
    DRAFT,
    PUBLISHED;

    public boolean isPublished() {
        return this == PUBLISHED;
    }
}
