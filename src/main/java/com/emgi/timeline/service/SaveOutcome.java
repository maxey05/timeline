package com.emgi.timeline.service;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.validation.ValidationResult;
import java.util.Objects;

public sealed interface SaveOutcome 
{
    record Saved(Idea idea) implements SaveOutcome
    {
        public Saved 
        {
            Objects.requireNonNull(idea, "idea");
        }
    }

    record Invalid(ValidationResult validation) implements SaveOutcome
    {
        public Invalid
        {
            Objects.requireNonNull(validation, "validation");
            if(validation.isValid())
            {
                throw new IllegalArgumentException(
                    "SaveOutcome.Invalid requires at least one validation error"
                );
            }
        }
    }

    record NotFound(IdeaId id) implements SaveOutcome
    {
        public NotFound
        {
            Objects.requireNonNull(id, "id");
        }
    }
}
