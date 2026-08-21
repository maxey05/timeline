package com.emgi.timeline.repository;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import java.util.List;
import java.util.Optional;

public interface IdeaRepository
{
    void save(Idea idea);

    Optional<Idea> findById(IdeaId id);

    List<Idea> findAll();

    boolean delete(IdeaId id);

    long count();
}
