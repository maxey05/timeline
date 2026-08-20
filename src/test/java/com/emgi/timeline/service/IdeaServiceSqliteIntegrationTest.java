package com.emgi.timeline.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.repository.sqlite.SqliteConnectionSource;
import com.emgi.timeline.repository.sqlite.SqliteIdeaRepository;
import com.emgi.timeline.support.FixedClock;
import com.emgi.timeline.support.IdeaFixtures;
import com.emgi.timeline.support.SqliteTestDatabase;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("IdeaService on real SQLite")
class IdeaServiceSqliteIntegrationTest {

    @TempDir
    Path tempDir;

    private SqliteConnectionSource connectionSource;
    private FixedClock clock;
    private IdeaService service;

    @BeforeEach
    void setUp() {
        connectionSource = SqliteTestDatabase.openFile(databaseFile());
        clock = FixedClock.atDefault();
        service = newService();
    }

    @AfterEach
    void tearDown() {
        connectionSource.close();
    }

    private Path databaseFile() {
        return tempDir.resolve("timeline.db");
    }

    private IdeaService newService() {
        return new IdeaService(new SqliteIdeaRepository(connectionSource), new IdeaValidator(),
                new com.emgi.timeline.support.SequentialIdGenerator(), clock);
    }

    private static Idea ideaOf(SaveOutcome outcome) {
        assertThat(outcome).isInstanceOf(SaveOutcome.Saved.class);
        return ((SaveOutcome.Saved) outcome).idea();
    }

    @Test
    @DisplayName("create, update and delete round-trip through the database")
    void fullRoundTrip() {
        Idea created = ideaOf(service.create(new CreateIdeaCommand(
                "Ship phase 2",
                Description.ofText("Storage and service."),
                IdeaFixtures.tags("java", "sqlite"),
                IdeaStatus.IN_PROGRESS)));
        assertThat(service.findAll()).containsExactly(created);

        clock.advance(Duration.ofHours(1));
        Idea updated = ideaOf(service.update(new UpdateIdeaCommand(
                created.id(), "Ship phase 2", Description.ofText("Done."),
                IdeaFixtures.tags("java"), IdeaStatus.COMPLETED)));
        assertThat(service.findById(created.id())).contains(updated);

        assertThat(service.delete(created.id())).isTrue();
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ideas are still there after the connection is closed and reopened")
    void dataSurvivesARestart() {
        Idea created = ideaOf(service.create(IdeaFixtures.aCreateCommand()));

        connectionSource.close();
        connectionSource = SqliteTestDatabase.openFile(databaseFile());

        assertThat(newService().findById(created.id())).contains(created);
    }

    @Test
    @DisplayName("an invalid create writes nothing to the database")
    void invalidCreateWritesNothing() {
        service.create(CreateIdeaCommand.of("   "));

        assertThat(service.count()).isEqualTo(0L);
    }
}
