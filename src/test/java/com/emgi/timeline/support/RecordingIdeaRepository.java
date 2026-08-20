package com.emgi.timeline.support;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.repository.IdeaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RecordingIdeaRepository implements IdeaRepository
{
    private final IdeaRepository delegate;
    private final List<Idea> saved = new ArrayList<>();
    private final List<IdeaId> deleted = new ArrayList<>();

    public RecordingIdeaRepository(IdeaRepository delegate) {
        this.delegate = delegate;
    }

    public RecordingIdeaRepository() {
        this(new com.emgi.timeline.repository.InMemoryIdeaRepository());
    }

    public List<Idea> saved() {
        return List.copyOf(saved);
    }

    public List<IdeaId> deleted() {
        return List.copyOf(deleted);
    }

    @Override
    public void save(Idea idea) {
        saved.add(idea);
        delegate.save(idea);
    }

    @Override
    public Optional<Idea> findById(IdeaId id) {
        return delegate.findById(id);
    }

    @Override
    public List<Idea> findAll() {
        return delegate.findAll();
    }

    @Override
    public boolean delete(IdeaId id) {
        deleted.add(id);
        return delegate.delete(id);
    }

    @Override
    public long count() {
        return delegate.count();
    }
}
