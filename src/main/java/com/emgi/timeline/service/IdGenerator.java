package com.emgi.timeline.service;

import com.emgi.timeline.domain.model.IdeaId;

@FunctionalInterface
public interface IdGenerator {

    IdeaId newId();
}
