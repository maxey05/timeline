package com.emgi.timeline.service;

import com.emgi.timeline.domain.model.IdeaId;

/**
 * Source of new idea ids. Injected rather than called statically so tests can substitute a
 * deterministic generator ({@code SequentialIdGenerator}) and assert on exact ids.
 *
 * <p>This interface is the only thing Phase 1 puts in {@code service/} — it exists now because
 * {@code SequentialIdGenerator} (a Phase 1 test fixture) has to implement something. The
 * implementation {@code UuidIdGenerator} and {@code IdeaService} itself are Phase 2.
 */
@FunctionalInterface
public interface IdGenerator {

    IdeaId newId();
}
