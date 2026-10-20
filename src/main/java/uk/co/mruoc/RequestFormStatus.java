package uk.co.mruoc;

public enum RequestFormStatus {
    DRAFT,
    SUBMITTED;

    public boolean isSubmitted() {
        return this == SUBMITTED;
    }
}
