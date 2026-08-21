package com.emgi.timeline.repository.sqlite;

import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
import com.emgi.timeline.domain.content.TextBlock;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.repository.StorageException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

public final class IdeaRowMapper
{
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_LINK = "LINK";
    public static final String TYPE_IMAGE = "IMAGE";

    public Idea toIdea(ResultSet row, Set<Tag> tags, List<ContentBlock> blocks) throws SQLException
    {
        String id = row.getString("id");
        return new Idea(
                IdeaId.fromString(id),
                row.getString("title"),
                new Description(blocks),
                tags,
                toStatus(row.getString("status"), id),
                toInstant(row.getString("created_at"), "created_at", id),
                toInstant(row.getString("updated_at"), "updated_at", id));
    }

    public Tag toTag(ResultSet row) throws SQLException
    {
        return Tag.of(row.getString("tag_name"));
    }

    public ContentBlock toBlock(ResultSet row) throws SQLException
    {
        String type = row.getString("type");
        return switch (type) {
            case TYPE_TEXT -> new TextBlock(nonNull(row.getString("text"), "text", type));
            case TYPE_LINK -> new LinkBlock(toUri(row.getString("uri"), type), row.getString("label"));
            case TYPE_IMAGE -> new ImageBlock(toUri(row.getString("uri"), type), row.getString("alt_text"));
            default -> throw new StorageException("Unknown block type in idea_block: " + type);
        };
    }

    public void bindIdea(PreparedStatement statement, Idea idea) throws SQLException
    {
        statement.setString(1, idea.id().toString());
        statement.setString(2, idea.title());
        statement.setString(3, idea.status().name());
        statement.setString(4, idea.createdAt().toString());
        statement.setString(5, idea.updatedAt().toString());
    }

    public void bindBlock(PreparedStatement statement, IdeaId ideaId, int position, ContentBlock block)
            throws SQLException {
        statement.setString(1, ideaId.toString());
        statement.setInt(2, position);
        switch (block) {
            case TextBlock text -> {
                statement.setString(3, TYPE_TEXT);
                statement.setString(4, text.text());
                statement.setNull(5, Types.VARCHAR);
                statement.setNull(6, Types.VARCHAR);
                statement.setNull(7, Types.VARCHAR);
            }
            case LinkBlock link -> {
                statement.setString(3, TYPE_LINK);
                statement.setNull(4, Types.VARCHAR);
                statement.setString(5, link.target().toString());
                statement.setString(6, link.label());
                statement.setNull(7, Types.VARCHAR);
            }
            case ImageBlock image -> {
                statement.setString(3, TYPE_IMAGE);
                statement.setNull(4, Types.VARCHAR);
                statement.setString(5, image.source().toString());
                statement.setNull(6, Types.VARCHAR);
                statement.setString(7, image.altText());
            }
        }
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

    private static URI toUri(String raw, String type)
    {
        try
        {
            return new URI(nonNull(raw, "uri", type));
        }
        catch (URISyntaxException e)
        {
            throw new StorageException("Stored " + type + " block has an unreadable uri: " + raw, e);
        }
    }

    private static String nonNull(String value, String column, String type)
    {
        if (value == null) {
            throw new StorageException("Stored " + type + " block is missing its " + column + " column");
        }
        return value;
    }
}
