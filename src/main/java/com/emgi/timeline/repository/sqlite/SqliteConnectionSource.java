package com.emgi.timeline.repository.sqlite;

import com.emgi.timeline.repository.StorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class SqliteConnectionSource implements AutoCloseable
{
    public static final String IN_MEMORY_URL = "jdbc:sqlite::memory:";

    private final String url;
    private final Path fileToCreate;
    private Connection connection;

    private SqliteConnectionSource(String url, Path fileToCreate)
    {
        this.url = Objects.requireNonNull(url, "url");
        this.fileToCreate = fileToCreate;
    }

    public static SqliteConnectionSource forFile(Path databaseFile)
    {
        Objects.requireNonNull(databaseFile, "databaseFile");
        Path absolute = databaseFile.toAbsolutePath();

        return new SqliteConnectionSource("jdbc:sqlite:" + absolute, absolute);
    }

    public static SqliteConnectionSource inMemory()
    {
        return new SqliteConnectionSource(IN_MEMORY_URL, null);
    }

    public static SqliteConnectionSource atDefaultLocation()
    {
        return forFile(defaultDatabaseFile());
    }

    public static Path defaultDatabaseFile()
    {
        return Path.of(System.getProperty("user.home"), ".timeline", "timeline.db");
    }

    public Connection connection()
    {
        if(connection == null)
        {
            connection = open();
        }

        return connection;
    }

    private Connection open()
    {
        if(fileToCreate != null)
        {
            Path parent = fileToCreate.getParent();
            if(parent != null)
            {
                try
                {
                    Files.createDirectories(parent);
                }
                catch (IOException e)
                {
                    throw new StorageException("Could not create the data directory: " + parent, e);
                }
            }
        }
        try
        {
            Connection opened = DriverManager.getConnection(url);
            try (Statement statement = opened.createStatement())
            {
                statement.execute("PRAGMA foreign_keys = ON");
            }
            return opened;
        }
        catch (SQLException e)
        {
            throw new StorageException("Could not open the Timeline database at " + url, e);
        }
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try 
        {
            connection.close();
        } 
        catch (SQLException e) 
        {
            throw new StorageException("Could not close the Timeline database at " + url, e);
        } 
        finally 
        {
            connection = null;
        }
    }
}
