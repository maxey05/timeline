package com.emgi.timeline.support;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Test data builders. Every test builds its ideas through here rather than calling the
 * seven-argument constructor, so adding a field to {@code Idea} later is a one-file change instead
 * of a hundred-line diff.
 *
 * <p>Defaults are deliberately boring: a valid idea that no assertion depends on unless the test
 * sets the field it cares about.
 */
public final class IdeaFixtures {

    public static final Instant T0 = FixedClock.DEFAULT_INSTANT;

    private IdeaFixtures() {
    }

    public static Builder anIdea() {
        return new Builder();
    }

    /** Shorthand for the very common "an idea with this title" case. */
    public static Idea ideaTitled(String title) {
        return anIdea().withTitle(title).build();
    }

    public static Set<Tag> tags(String... names) {
        return Arrays.stream(names)
                .map(Tag::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static CreateIdeaCommand aCreateCommand() {
        return new CreateIdeaCommand("An idea", Description.ofText("Body"), tags("java"),
                IdeaStatus.INCOMPLETE);
    }

    public static UpdateIdeaCommand anUpdateCommand() {
        return new UpdateIdeaCommand(SequentialIdGenerator.idFor(1), "An idea",
                Description.ofText("Body"), tags("java"), IdeaStatus.IN_PROGRESS);
    }

    public static final class Builder {

        private IdeaId id = SequentialIdGenerator.idFor(1);
        private String title = "An idea";
        private Description description = Description.empty();
        private Set<Tag> tags = Set.of();
        private IdeaStatus status = IdeaStatus.INCOMPLETE;
        private Instant createdAt = T0;
        private Instant updatedAt = T0;

        public Builder withId(IdeaId newId) {
            this.id = newId;
            return this;
        }

        /** Convenience for the sequential ids {@code SequentialIdGenerator} produces. */
        public Builder withIdNumber(long n) {
            return withId(SequentialIdGenerator.idFor(n));
        }

        public Builder withTitle(String newTitle) {
            this.title = newTitle;
            return this;
        }

        public Builder withDescription(Description newDescription) {
            this.description = newDescription;
            return this;
        }

        public Builder withText(String text) {
            return withDescription(Description.ofText(text));
        }

        public Builder withTags(String... tagNames) {
            this.tags = tags(tagNames);
            return this;
        }

        public Builder withTags(Set<Tag> newTags) {
            this.tags = newTags;
            return this;
        }

        public Builder withStatus(IdeaStatus newStatus) {
            this.status = newStatus;
            return this;
        }

        public Builder createdAt(Instant instant) {
            this.createdAt = instant;
            return this;
        }

        public Builder createdAt(String isoInstant) {
            return createdAt(Instant.parse(isoInstant));
        }

        public Builder updatedAt(Instant instant) {
            this.updatedAt = instant;
            return this;
        }

        public Idea build() {
            return new Idea(id, title, description, tags, status, createdAt, updatedAt);
        }
    }
}
