package com.emgi.timeline.service;

import com.emgi.timeline.domain.model.IdeaId;

public final class UuidIdGenerator implements IdGenerator
{
    @Override
    public IdeaId newId()
    {
        return IdeaId.newId();
    }
}
