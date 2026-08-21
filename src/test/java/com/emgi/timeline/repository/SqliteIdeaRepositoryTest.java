package com.emgi.timeline.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.repository.sqlite.SqliteConnectionSource;
import com.emgi.timeline.repository.sqlite.SqliteIdeaRepository;
import com.emgi.timeline.support.IdeaFixtures;
import com.emgi.timeline.support.SqliteTestDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SqliteIdeaRepository (contract + persistence)")
class SqliteIdeaRepositoryTest extends IdeaRepositoryContractTest
{
    @TempDir
    Path tempDir;

    private SqliteConnectionSource connectionSource;

    @Override
    protected IdeaRepository createRepository() {
        connectionSource = SqliteTestDatabase.openFile(databaseFile());
        return new SqliteIdeaRepository(connectionSource);
    }

    @AfterEach
    void closeConnection() {
        if (connectionSource != null) {
            connectionSource.close();
        }
    }

    private Path databaseFile() {
        return tempDir.resolve("data").resolve("timeline.db");
    }

    @Test
    @DisplayName("the database file and its parent directory are created on first use")
    void createsDatabaseFileAndDirectory() {
        assertThat(Files.exists(databaseFile())).isTrue();
        assertThat(Files.isDirectory(databaseFile().getParent())).isTrue();
    }

    @Test
    @DisplayName("data survives closing the connection and reopening the same file")
    void dataSurvivesAReopen() {
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withTitle("Persisted")
                .withText("Still here after a restart.")
                .withTags("java")
                .build();
        repository().save(idea);

        connectionSource.close();
        connectionSource = SqliteTestDatabase.openFile(databaseFile());
        IdeaRepository reopened = new SqliteIdeaRepository(connectionSource);

        assertThat(reopened.findById(idea.id())).contains(idea);
    }

    @Test
    @DisplayName("deleting an idea cascades to its tag and block rows")
    void deleteCascadesToChildRows() throws SQLException {
        Idea idea = IdeaFixtures.anIdea()
                .withIdNumber(1)
                .withTags("java", "storage")
                .withText("A block that must not outlive its idea.")
                .build();
        repository().save(idea);
        assertThat(rowCount("idea_tag")).isEqualTo(2L);
        assertThat(rowCount("idea_block")).isEqualTo(1L);

        repository().delete(idea.id());

        assertThat(rowCount("idea")).isEqualTo(0L);
        assertThat(rowCount("idea_tag")).isEqualTo(0L);
        assertThat(rowCount("idea_block")).isEqualTo(0L);
    }

    @Test
    @DisplayName("foreign keys are enforced on the connection (PRAGMA foreign_keys = ON)")
    void foreignKeysAreEnforced() {
        assertThatThrownBy(() -> {
            try (Statement statement = connectionSource.connection().createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO idea_tag (idea_id, tag_name) VALUES ('missing-idea', 'orphan')");
            }
        }).isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("applying the schema twice is harmless")
    void schemaInitializationIsIdempotent() {
        try (SqliteConnectionSource second = SqliteTestDatabase.openFile(databaseFile())) {
            assertThat(repository().count()).isEqualTo(0L);
        }
    }

    private long rowCount(String table) throws SQLException {
        try (Statement statement = connectionSource.connection().createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rows.next() ? rows.getLong(1) : -1L;
        }
    }
}
