package com.emgi.timeline.repository;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryIdeaRepository implements IdeaRepository
{
    private final Map<IdeaId, Idea> ideasById = new LinkedHashMap<>();

    @Override
    public void save(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");
        ideasById.put(idea.id(), idea);
    }

    @Override
    public Optional<Idea> findById(IdeaId id)
    {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(ideasById.get(id));
    }

    @Override
    public List<Idea> findAll()
    {
        return List.copyOf(ideasById.values());
    }

    @Override
    public boolean delete(IdeaId id)
    {
        Objects.requireNonNull(id, "id");
        return ideasById.remove(id) != null;
    }

    @Override
    public long count()
    {
        return ideasById.size();
    }
}
