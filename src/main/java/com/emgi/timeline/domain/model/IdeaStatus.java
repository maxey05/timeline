package com.emgi.timeline.domain.model;

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
