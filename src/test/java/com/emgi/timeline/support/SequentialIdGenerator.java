package com.emgi.timeline.support;

import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.service.IdGenerator;
import java.util.UUID;

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

    public static IdeaId idFor(long n) {
        return new IdeaId(new UUID(0L, n));
    }
}
