package com.emgi.timeline.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.support.FixedClock;
import com.emgi.timeline.support.IdeaFixtures;
import com.emgi.timeline.support.RecordingIdeaRepository;
import com.emgi.timeline.support.SequentialIdGenerator;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IdeaService")
class IdeaServiceTest {

    private static final Instant T0 = FixedClock.DEFAULT_INSTANT;

    private RecordingIdeaRepository repository;
    private FixedClock clock;
    private IdeaService service;

    @BeforeEach
    void setUp() {
        repository = new RecordingIdeaRepository();
        clock = FixedClock.atDefault();
        service = new IdeaService(repository, new IdeaValidator(), new SequentialIdGenerator(), clock);
    }

    private static Idea ideaOf(SaveOutcome outcome) {
        assertThat(outcome).isInstanceOf(SaveOutcome.Saved.class);
        return ((SaveOutcome.Saved) outcome).idea();
    }

    @Test
    @DisplayName("create assigns the id from the generator")
    void createAssignsGeneratedId() {
        Idea idea = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        assertThat(idea.id()).isEqualTo(SequentialIdGenerator.idFor(1));
    }

    @Test
    @DisplayName("create stamps createdAt and updatedAt from the clock, identically")
    void createStampsBothTimestampsFromTheClock() {
        Idea idea = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        assertThat(idea.createdAt()).isEqualTo(T0);
        assertThat(idea.updatedAt()).isEqualTo(T0);
    }

    @Test
    @DisplayName("create copies the command's fields onto the idea")
    void createCopiesCommandFields() {
        CreateIdeaCommand command = new CreateIdeaCommand(
                "Storage layer",
                Description.ofText("Contract tests first."),
                IdeaFixtures.tags("java", "storage"),
                IdeaStatus.IN_PROGRESS);

        Idea idea = ideaOf(service.create(command));

        assertThat(idea.title()).isEqualTo("Storage layer");
        assertThat(idea.description()).isEqualTo(command.description());
        assertThat(idea.tags()).isEqualTo(command.tags());
        assertThat(idea.status()).isEqualTo(IdeaStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("create strips surrounding whitespace from the title")
    void createStripsTitle() {
        Idea idea = ideaOf(service.create(CreateIdeaCommand.of("   Padded title   ")));

        assertThat(idea.title()).isEqualTo("Padded title");
    }

    @Test
    @DisplayName("create persists the idea exactly once")
    void createPersistsOnce() {
        Idea idea = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        assertThat(repository.saved()).containsExactly(idea);
        assertThat(service.findById(idea.id())).contains(idea);
    }

    @Test
    @DisplayName("create with a blank title reports a title error")
    void createRejectsBlankTitle() {
        SaveOutcome outcome = service.create(CreateIdeaCommand.of("   "));

        assertThat(outcome).isInstanceOf(SaveOutcome.Invalid.class);
        assertThat(((SaveOutcome.Invalid) outcome).validation().messagesFor(IdeaValidator.FIELD_TITLE))
                .containsExactly("Title is required.");
    }

    @Test
    @DisplayName("invalid input never reaches the repository")
    void invalidCreateNeverReachesStorage() {
        service.create(CreateIdeaCommand.of(" "));

        assertThat(repository.saved()).isEmpty();
        assertThat(repository.count()).isEqualTo(0L);
    }

    @Test
    @DisplayName("ids are unique even when two ideas are created at the same instant")
    void idsAreUniqueWithinTheSameInstant() {
        Idea first = ideaOf(service.create(IdeaFixtures.aCreateCommand()));
        Idea second = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        assertThat(first.createdAt()).isEqualTo(second.createdAt());
        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    @DisplayName("update preserves createdAt and bumps updatedAt")
    void updatePreservesCreatedAtAndBumpsUpdatedAt() {
        Idea created = ideaOf(service.create(IdeaFixtures.aCreateCommand()));
        clock.advance(Duration.ofMinutes(5));

        Idea updated = ideaOf(service.update(new UpdateIdeaCommand(
                created.id(), "Renamed", Description.ofText("New body"),
                IdeaFixtures.tags("kotlin"), IdeaStatus.COMPLETED)));

        assertThat(updated.createdAt()).isEqualTo(T0);
        assertThat(updated.updatedAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("update applies every changed field")
    void updateAppliesEveryField() {
        Idea created = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        Idea updated = ideaOf(service.update(new UpdateIdeaCommand(
                created.id(), "Renamed", Description.ofText("New body"),
                IdeaFixtures.tags("kotlin"), IdeaStatus.COMPLETED)));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.title()).isEqualTo("Renamed");
        assertThat(updated.description()).isEqualTo(Description.ofText("New body"));
        assertThat(updated.tags()).isEqualTo(IdeaFixtures.tags("kotlin"));
        assertThat(updated.status()).isEqualTo(IdeaStatus.COMPLETED);
    }

    @Test
    @DisplayName("update on an unknown id reports NotFound and writes nothing")
    void updateUnknownIdIsNotFound() {
        IdeaId unknown = SequentialIdGenerator.idFor(99);

        SaveOutcome outcome = service.update(new UpdateIdeaCommand(
                unknown, "Anything", null, null, null));

        assertThat(outcome).isEqualTo(new SaveOutcome.NotFound(unknown));
        assertThat(repository.saved()).isEmpty();
    }

    @Test
    @DisplayName("an invalid update leaves the stored idea untouched")
    void invalidUpdateLeavesStoredIdeaUntouched() {
        Idea created = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        SaveOutcome outcome = service.update(new UpdateIdeaCommand(
                created.id(), "  ", null, null, null));

        assertThat(outcome).isInstanceOf(SaveOutcome.Invalid.class);
        assertThat(repository.saved()).containsExactly(created);
        assertThat(service.findById(created.id())).contains(created);
    }

    @Test
    @DisplayName("delete removes an existing idea and reports it")
    void deleteRemovesExistingIdea() {
        Idea created = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        assertThat(service.delete(created.id())).isTrue();
        assertThat(service.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("deleting an unknown id is a no-op")
    void deleteUnknownIdIsNoOp() {
        assertThat(service.delete(SequentialIdGenerator.idFor(42))).isFalse();
    }

    @Test
    @DisplayName("findAll and count expose what was created")
    void findAllAndCountExposeCreatedIdeas() {
        Idea first = ideaOf(service.create(IdeaFixtures.aCreateCommand()));
        Idea second = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        assertThat(service.findAll()).containsExactlyInAnyOrder(first, second);
        assertThat(service.count()).isEqualTo(2L);
    }
}
