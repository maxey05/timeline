package com.emgi.timeline.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.emgi.timeline.support.IdeaFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryIdeaRepository (contract)")
class InMemoryIdeaRepositoryTest extends IdeaRepositoryContractTest
{
    @Override
    protected IdeaRepository createRepository()
    {
        return new InMemoryIdeaRepository();
    }

    @Test
    @DisplayName("two instances do not share a state")
    void instancesAreIndependent()
    {
        repository().save(IdeaFixtures.anIdea().withIdNumber(1).build());

        assertThat(new InMemoryIdeaRepository().count()).isEqualTo(0L);
    }
}
