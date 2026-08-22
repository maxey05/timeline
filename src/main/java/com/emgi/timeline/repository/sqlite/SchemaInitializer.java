package com.emgi.timeline.repository.sqlite;

import com.emgi.timeline.repository.StorageException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class SchemaInitializer
{
    public static final String SCHEMA_RESOURCE = "/com/emgi/timeline/db/schema.sql";

    public void initialize(Connection connection)
    {
        Objects.requireNonNull(connection, "connection");
        for(String statementSql : statements(readSchema()))
        {
            try(Statement statement = connection.createStatement())
            {
                statement.execute(statementSql);
            } catch (SQLException e)
            {
                throw new StorageException("Could not apply schema statement: " + statementSql, e);
            }
        }

        /*
         * schema.sql only ever runs CREATE TABLE IF NOT EXISTS, so it cannot reshape a
         * database that already exists. Anything that has to change an existing file --
         * today, folding idea_block into idea.description -- belongs here, after it.
         */
        LegacyBlockMigration.apply(connection);
    }

    static String readSchema()
    {
        try(InputStream in = SchemaInitializer.class.getResourceAsStream(SCHEMA_RESOURCE))
        {
            if(in == null)
            {
                throw new StorageException("Missing classpath resource: " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new StorageException("Could not read " + SCHEMA_RESOURCE, e);
        }
    }

    static List<String> statements(String sql)
    {
        StringBuilder withoutComments = new StringBuilder();
        for(String line : sql.lines().toList())
        {
            int comment = line.indexOf("--");
            withoutComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for(String candidate : Arrays.asList(withoutComments.toString().split(";")))
        {
            String trimmed = candidate.strip();
            if(!trimmed.isEmpty())
            {
                statements.add(trimmed);
            }
        }

        return List.copyOf(statements);
    }

}
