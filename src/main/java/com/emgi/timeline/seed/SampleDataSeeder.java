package com.emgi.timeline.seed;

import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.repository.IdeaRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Development scaffolding: puts a handful of ideas in an empty database so Phase 3's list has
 * something to render before Phase 4 makes it possible to type one in.
 *
 * <p>Deliberately writes {@link Idea} records straight to the repository rather than going through
 * {@code IdeaService.create(...)}: the service stamps every idea with the current instant, which
 * would make all the samples identical in age and hide the newest-first ordering.
 *
 * <p>Each sample exists to prove one rendering rule — see {@link #samples()}.
 *
 * <p><strong>Delete this class, its test and its call in {@code App} when Phase 4 lands.</strong>
 */
public final class SampleDataSeeder
{
    /** How many ideas {@link #seedIfEmpty(IdeaRepository)} writes into an empty repository. */
    public static final int SAMPLE_COUNT = 5;

    private final Clock clock;

    public SampleDataSeeder(Clock clock)
    {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Writes the samples only if the repository is empty.
     *
     * @return the number of ideas written — 0 on every launch after the first
     */
    public int seedIfEmpty(IdeaRepository repository)
    {
        Objects.requireNonNull(repository, "repository");

        if(repository.count() != 0)
        {
            return 0;
        }

        List<Idea> samples = samples();
        for(Idea idea : samples)
        {
            repository.save(idea);
        }

        return samples.size();
    }

    /**
     * The samples, newest first. Ages are relative to the clock, never hardcoded instants, so the
     * list doesn't age badly the week after it was written.
     */
    private List<Idea> samples()
    {
        Instant now = clock.instant();

        return List.of(
            // "2 hours ago" plus a multi-tag row.
            sample(
                "Rewrite the scheduler",
                Description.ofText("A cleaner approach to the priority queue. The current one "
                    + "rebuilds the heap on every insert."),
                Set.of(Tag.of("java"), Tag.of("school")),
                IdeaStatus.IN_PROGRESS,
                now.minus(Duration.ofHours(2))),

            // "2 days ago" — the exact case §6.2's mockup shows.
            sample(
                "Portfolio site ideas",
                Description.ofText("Static, no framework. Notes and refs below."),
                Set.of(Tag.of("web")),
                IdeaStatus.INCOMPLETE,
                now.minus(Duration.ofDays(2))),

            // The absolute date form, and a row with neither tags nor a description: proves
            // neither one collapses the layout or leaves a dead band of whitespace.
            sample(
                "Read the JavaFX layout documentation end to end",
                Description.empty(),
                Set.of(),
                IdeaStatus.COMPLETED,
                now.minus(Duration.ofDays(17))),

            // A description well past 120 characters: proves preview truncation and the ellipsis.
            sample(
                "Toy database engine",
                Description.ofText("Start with an append-only log and a hash index over it, then "
                    + "add a B-tree once reads outnumber writes. The point is understanding why "
                    + "the page cache exists, not shipping anything."),
                Set.of(Tag.of("java")),
                IdeaStatus.INCOMPLETE,
                now.minus(Duration.ofDays(40))),

            // Over a year old, so the year shows; a long title with a non-ASCII character, so
            // title ellipsis and a horizontal scrollbar would both be obvious if they were wrong.
            sample(
                "Work through the concurrency chapters properly — locks, then lock-free "
                    + "structures, then the memory model, with notes",
                Description.ofText("One chapter a week. Write the summary before moving on."),
                Set.of(Tag.of("reading"), Tag.of("long-term")),
                IdeaStatus.IN_PROGRESS,
                now.minus(Duration.ofDays(400))));
    }

    private static Idea sample(String title, Description description, Set<Tag> tags,
                               IdeaStatus status, Instant createdAt)
    {
        // updatedAt == createdAt: nothing has been edited.
        return new Idea(IdeaId.newId(), title, description, tags, status, createdAt, createdAt);
    }
}
