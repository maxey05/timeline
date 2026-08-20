package com.emgi.timeline.repository.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchemaInitializer")
class SchemaInitializerTest {

    @Test
    @DisplayName("schema.sql is on the classpath and splits into the three CREATE TABLE statements")
    void schemaSplitsIntoThreeStatements() {
        List<String> statements = SchemaInitializer.statements(SchemaInitializer.readSchema());

        assertThat(statements).hasSize(3);
        assertThat(statements).allSatisfy(sql -> assertThat(sql).startsWith("CREATE TABLE IF NOT EXISTS"));
        assertThat(statements).allSatisfy(sql -> assertThat(sql).doesNotContain("--"));
    }

    @Test
    @DisplayName("initialize creates the three tables in an empty database")
    void createsTheThreeTables() throws SQLException {
        try (SqliteConnectionSource source = SqliteConnectionSource.inMemory()) {
            new SchemaInitializer().initialize(source.connection());

            assertThat(tableNames(source)).contains("idea", "idea_tag", "idea_block");
        }
    }

    @Test
    @DisplayName("initialize is idempotent — running it on an initialized database changes nothing")
    void isIdempotent() throws SQLException {
        try (SqliteConnectionSource source = SqliteConnectionSource.inMemory()) {
            SchemaInitializer initializer = new SchemaInitializer();
            initializer.initialize(source.connection());
            initializer.initialize(source.connection());

            assertThat(tableNames(source)).contains("idea", "idea_tag", "idea_block");
        }
    }

    private static List<String> tableNames(SqliteConnectionSource source) throws SQLException {
        try (Statement statement = source.connection().createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table'")) {
            List<String> names = new java.util.ArrayList<>();
            while (rows.next()) {
                names.add(rows.getString(1));
            }
            return names;
        }
    }
}
