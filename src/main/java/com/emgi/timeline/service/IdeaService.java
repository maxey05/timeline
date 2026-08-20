package com.emgi.timeline.service;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.domain.validation.ValidationResult;
import com.emgi.timeline.repository.IdeaRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class IdeaService 
{
    private final IdeaRepository repository;
    private final IdeaValidator validator;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public IdeaService(IdeaRepository repository, IdeaValidator validator, IdGenerator idGenerator,
                       Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SaveOutcome create(CreateIdeaCommand command) {
        Objects.requireNonNull(command, "command");
        ValidationResult validation = validator.validate(command);
        if (validation.isInvalid()) {
            return new SaveOutcome.Invalid(validation);
        }
        Instant now = clock.instant();
        Idea idea = new Idea(
                idGenerator.newId(),
                command.title().strip(),
                command.description(),
                command.tags(),
                command.status(),
                now,
                now);
        repository.save(idea);
        return new SaveOutcome.Saved(idea);
    }

    public SaveOutcome update(UpdateIdeaCommand command) {
        Objects.requireNonNull(command, "command");
        Optional<Idea> existing = repository.findById(command.id());
        if (existing.isEmpty()) {
            return new SaveOutcome.NotFound(command.id());
        }
        ValidationResult validation = validator.validate(command);
        if (validation.isInvalid()) {
            return new SaveOutcome.Invalid(validation);
        }
        Idea updated = existing.get()
                .withTitle(command.title().strip())
                .withDescription(command.description())
                .withTags(command.tags())
                .withStatus(command.status())
                .withUpdatedAt(clock.instant());
        repository.save(updated);
        return new SaveOutcome.Saved(updated);
    }

    public boolean delete(IdeaId id) {
        Objects.requireNonNull(id, "id");
        return repository.delete(id);
    }

    public Optional<Idea> findById(IdeaId id) {
        Objects.requireNonNull(id, "id");
        return repository.findById(id);
    }

    public List<Idea> findAll() {
        return repository.findAll();
    }

    public long count() {
        return repository.count();
    }
}
