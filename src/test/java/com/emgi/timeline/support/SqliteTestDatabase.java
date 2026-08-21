package com.emgi.timeline.support;

import com.emgi.timeline.repository.sqlite.SchemaInitializer;
import com.emgi.timeline.repository.sqlite.SqliteConnectionSource;
import java.nio.file.Path;

public final class SqliteTestDatabase
{
    private SqliteTestDatabase()
    {}

    public static SqliteConnectionSource openFile(Path databaseFile)
    {
        return initialize(SqliteConnectionSource.forFile(databaseFile));
    }

    public static SqliteConnectionSource openInMemory()
    {
        return initialize(SqliteConnectionSource.inMemory());
    }

    private static SqliteConnectionSource initialize(SqliteConnectionSource source)
    {
        new SchemaInitializer().initialize(source.connection());
        return source;
    }
}
