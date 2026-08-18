package com.emgi.timeline.domain.model;

/**
 * Where an idea stands. Locked decision #2 — status is in V1.
 *
 * <p>The display label lives on the constant rather than in a {@code switch} elsewhere, so adding
 * a status later is a one-line change in one file, and the compiler requires the label.
 * Views must render {@link #displayName()} and never hardcode these strings; Phase 5's sort/filter
 * controls populate themselves from {@link #values()}.
 */
public enum IdeaStatus {

    INCOMPLETE("Incomplete"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed");

    private final String displayName;

    IdeaStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
