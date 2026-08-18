package com.emgi.timeline.support;

import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.service.IdGenerator;
import java.util.UUID;

/**
 * Hands out predictable ids — {@code 0000...0001}, {@code 0000...0002}, and so on — so tests can
 * assert on exact values and on ordering.
 *
 * <p>Ids ascend in both numeric and lexicographic order, which matters because
 * {@code SortOrder}'s tiebreak compares ids as strings.
 */
public final class SequentialIdGenerator implements IdGenerator {

    private long next;

    public SequentialIdGenerator() {
        this(1);
    }

    public SequentialIdGenerator(long startAt) {
        this.next = startAt;
    }

    @Override
    public IdeaId newId() {
        return idFor(next++);
    }

    /** The id this generator would produce for sequence number {@code n}, without consuming it. */
    public static IdeaId idFor(long n) {
        return new IdeaId(new UUID(0L, n));
    }
}
