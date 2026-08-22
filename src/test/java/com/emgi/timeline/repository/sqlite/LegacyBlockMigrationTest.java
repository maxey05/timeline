package com.emgi.timeline.repository.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LegacyBlockMigration")
class LegacyBlockMigrationTest {

    private static final String OLD_IDEA_TABLE = """
            CREATE TABLE idea (
                id         TEXT PRIMARY KEY,
                title      TEXT NOT NULL,
                status     TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL)
            """;

    private static final String OLD_BLOCK_TABLE = """
            CREATE TABLE idea_block (
                idea_id  TEXT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
                position INTEGER NOT NULL,
                type     TEXT NOT NULL,
                text     TEXT,
                uri      TEXT,
                label    TEXT,
                alt_text TEXT,
                PRIMARY KEY (idea_id, position))
            """;

    private static final String INSERT_BLOCK = """
            INSERT INTO idea_block (idea_id, position, type, text, uri, label, alt_text)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    @Test
    @DisplayName("every block type folds into the one description, in order")
    void everyBlockTypeFoldsIntoTheDescription() throws SQLException {
        try (SqliteConnectionSource source = SqliteConnectionSource.inMemory()) {
            Connection connection = source.connection();
            givenAnOldDatabase(connection);
            givenAnIdea(connection, "i1");
            givenBlock(connection, "i1", 0, "TEXT", "Intro.", null, null, null);
            givenBlock(connection, "i1", 1, "LINK", null, "https://example.com/spec", "The spec", null);
            givenBlock(connection, "i1", 2, "IMAGE", null, "file:///d.png", null, "Layers");
            givenBlock(connection, "i1", 3, "TEXT", "Closing.", null, null, null);

            new SchemaInitializer().initialize(connection);

            assertThat(descriptionOf(connection, "i1")).isEqualTo(
                    "Intro.\n\n"
                            + "The spec (https://example.com/spec)\n\n"
                            + "![Layers](file:///d.png)\n\n"
                            + "Closing.");
        }
    }

    @Test
    @DisplayName("a link whose label was only ever its own URL does not say it twice")
    void anUnlabelledLinkBecomesJustItsAddress() throws SQLException {
        try (SqliteConnectionSource source = SqliteConnectionSource.inMemory()) {
            Connection connection = source.connection();
            givenAnOldDatabase(connection);
            givenAnIdea(connection, "i1");
            givenBlock(connection, "i1", 0, "LINK", null, "https://example.com/x",
                    "https://example.com/x", null);
            givenBlock(connection, "i1", 1, "LINK", null, "https://example.com/y", "", null);

            new SchemaInitializer().initialize(connection);

            assertThat(descriptionOf(connection, "i1"))
                    .isEqualTo("https://example.com/x\n\nhttps://example.com/y");
        }
    }

    @Test
    @DisplayName("the old table is dropped, so the migration never runs twice")
    void theOldTableIsDropped() throws SQLException {
        try (SqliteConnectionSource source = SqliteConnectionSource.inMemory()) {
            Connection connection = source.connection();
            givenAnOldDatabase(connection);
            givenAnIdea(connection, "i1");
            givenBlock(connection, "i1", 0, "TEXT", "Body", null, null, null);

            SchemaInitializer initializer = new SchemaInitializer();
            initializer.initialize(connection);
            initializer.initialize(connection);

            assertThat(tableNames(connection)).doesNotContain("idea_block");
            assertThat(descriptionOf(connection, "i1")).isEqualTo("Body");
        }
    }

    @Test
    @DisplayName("an old idea with no blocks at all comes through with an empty description")
    void anIdeaWithNoBlocksGetsAnEmptyDescription() throws SQLException {
        try (SqliteConnectionSource source = SqliteConnectionSource.inMemory()) {
            Connection connection = source.connection();
            givenAnOldDatabase(connection);
            givenAnIdea(connection, "i1");

            new SchemaInitializer().initialize(connection);

            assertThat(descriptionOf(connection, "i1")).isEmpty();
        }
    }

    @Test
    @DisplayName("a fresh database needs no migration and is left exactly as schema.sql made it")
    void aFreshDatabaseIsUntouched() throws SQLException {
        try (SqliteConnectionSource source = SqliteConnectionSource.inMemory()) {
            Connection connection = source.connection();

            new SchemaInitializer().initialize(connection);

            assertThat(tableNames(connection)).contains("idea", "idea_tag");
            assertThat(tableNames(connection)).doesNotContain("idea_block");
        }
    }


    private static void givenAnOldDatabase(Connection connection) throws SQLException {
        execute(connection, OLD_IDEA_TABLE);
        execute(connection, """
                CREATE TABLE idea_tag (
                    idea_id  TEXT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
                    tag_name TEXT NOT NULL,
                    PRIMARY KEY (idea_id, tag_name))
                """);
        execute(connection, OLD_BLOCK_TABLE);
    }

    private static void givenAnIdea(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO idea (id, title, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, "An idea");
            statement.setString(3, "INCOMPLETE");
            statement.setString(4, "2026-01-01T00:00:00Z");
            statement.setString(5, "2026-01-01T00:00:00Z");
            statement.executeUpdate();
        }
    }

    private static void givenBlock(Connection connection, String ideaId, int position, String type,
            String text, String uri, String label, String altText) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_BLOCK)) {
            statement.setString(1, ideaId);
            statement.setInt(2, position);
            statement.setString(3, type);
            statement.setString(4, text);
            statement.setString(5, uri);
            statement.setString(6, label);
            statement.setString(7, altText);
            statement.executeUpdate();
        }
    }

    private static String descriptionOf(Connection connection, String ideaId) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT description FROM idea WHERE id = ?")) {
            statement.setString(1, ideaId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table'")) {
            List<String> names = new ArrayList<>();
            while (rows.next()) {
                names.add(rows.getString(1));
            }
            return names;
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
