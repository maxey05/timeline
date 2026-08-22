package com.emgi.timeline.repository.sqlite;

import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.repository.StorageException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

public final class IdeaRowMapper
{
    public Idea toIdea(ResultSet row, Set<Tag> tags) throws SQLException
    {
        String id = row.getString("id");
        return new Idea(
                IdeaId.fromString(id),
                row.getString("title"),
                new Description(text(row.getString("description"))),
                tags,
                toStatus(row.getString("status"), id),
                toInstant(row.getString("created_at"), "created_at", id),
                toInstant(row.getString("updated_at"), "updated_at", id));
    }

    public Tag toTag(ResultSet row) throws SQLException
    {
        return Tag.of(row.getString("tag_name"));
    }

    public void bindIdea(PreparedStatement statement, Idea idea) throws SQLException
    {
        statement.setString(1, idea.id().toString());
        statement.setString(2, idea.title());
        statement.setString(3, idea.description().text());
        statement.setString(4, idea.status().name());
        statement.setString(5, idea.createdAt().toString());
        statement.setString(6, idea.updatedAt().toString());
    }

    private static String text(String raw)
    {
        return raw == null ? "" : raw;
    }

    private static IdeaStatus toStatus(String raw, String ideaId)
    {
        try
        {
            return IdeaStatus.valueOf(raw);
        }
        catch (IllegalArgumentException | NullPointerException e)
        {
            throw new StorageException("Idea " + ideaId + " has an unknown status: " + raw, e);
        }
    }

    private static Instant toInstant(String raw, String column, String ideaId)
    {
        try
        {
            return Instant.parse(raw);
        }
        catch (DateTimeParseException | NullPointerException e)
        {
            throw new StorageException(
                    "Idea " + ideaId + " has an unreadable " + column + ": " + raw, e);
        }
    }
}
