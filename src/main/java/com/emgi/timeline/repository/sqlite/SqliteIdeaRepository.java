package com.emgi.timeline.repository.sqlite;

import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.repository.IdeaRepository;
import com.emgi.timeline.repository.StorageException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SqliteIdeaRepository implements IdeaRepository
{
    private static final String UPSERT_IDEA = """
            INSERT INTO idea (id, title, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title      = excluded.title,
                status     = excluded.status,
                created_at = excluded.created_at,
                updated_at = excluded.updated_at
            """;
    private static final String DELETE_TAGS = "DELETE FROM idea_tag WHERE idea_id = ?";
    private static final String INSERT_TAG = "INSERT INTO idea_tag (idea_id, tag_name) VALUES (?, ?)";
    private static final String DELETE_BLOCKS = "DELETE FROM idea_block WHERE idea_id = ?";
    private static final String INSERT_BLOCK = """
            INSERT INTO idea_block (idea_id, position, type, text, uri, label, alt_text)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_IDEA = "SELECT * FROM idea WHERE id = ?";
    private static final String SELECT_ALL_IDEAS = "SELECT * FROM idea";
    private static final String SELECT_TAGS = "SELECT tag_name FROM idea_tag WHERE idea_id = ?";
    private static final String SELECT_ALL_TAGS = "SELECT idea_id, tag_name FROM idea_tag";
    private static final String SELECT_BLOCKS =
            "SELECT * FROM idea_block WHERE idea_id = ? ORDER BY position";
    private static final String SELECT_ALL_BLOCKS =
            "SELECT * FROM idea_block ORDER BY idea_id, position";
    private static final String DELETE_IDEA = "DELETE FROM idea WHERE id = ?";
    private static final String COUNT_IDEAS = "SELECT COUNT(*) FROM idea";

    private final SqliteConnectionSource connectionSource;
    private final IdeaRowMapper rowMapper;

    public SqliteIdeaRepository(SqliteConnectionSource connectionSource, IdeaRowMapper rowMapper) 
    {
        this.connectionSource = Objects.requireNonNull(connectionSource, "connectionSource");
        this.rowMapper = Objects.requireNonNull(rowMapper, "rowMapper");
    }

    public SqliteIdeaRepository(SqliteConnectionSource connectionSource) 
    {
        this(connectionSource, new IdeaRowMapper());
    }

    @Override
    public void save(Idea idea) 
    {
        Objects.requireNonNull(idea, "idea");
        Connection connection = connectionSource.connection();
        boolean autoCommitBefore;
        try 
        {
            autoCommitBefore = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } 
        catch (SQLException e) 
        {
            throw new StorageException("Could not begin a transaction for idea " + idea.id(), e);
        }
        try 
        {
            writeIdeaRow(connection, idea);
            replaceTags(connection, idea);
            replaceBlocks(connection, idea);
            connection.commit();
        } 
        catch (SQLException e) 
        {
            rollback(connection, idea.id(), e);
            throw new StorageException("Could not save idea " + idea.id(), e);
        } 
        finally 
        {
            restoreAutoCommit(connection, autoCommitBefore);
        }
    }

    private void writeIdeaRow(Connection connection, Idea idea) throws SQLException 
    {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_IDEA)) 
        {
            rowMapper.bindIdea(statement, idea);
            statement.executeUpdate();
        }
    }

    private void replaceTags(Connection connection, Idea idea) throws SQLException 
    {
        try (PreparedStatement delete = connection.prepareStatement(DELETE_TAGS)) 
        {
            delete.setString(1, idea.id().toString());
            delete.executeUpdate();
        }
        if (idea.tags().isEmpty()) 
            {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(INSERT_TAG)) 
        {
            for (Tag tag : idea.tags()) 
            {
                insert.setString(1, idea.id().toString());
                insert.setString(2, tag.name());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void replaceBlocks(Connection connection, Idea idea) throws SQLException 
    {
        try (PreparedStatement delete = connection.prepareStatement(DELETE_BLOCKS)) 
        {
            delete.setString(1, idea.id().toString());
            delete.executeUpdate();
        }
        List<ContentBlock> blocks = idea.description().blocks();
        if (blocks.isEmpty()) 
            {
            return;
        }
        try (PreparedStatement insert = connection.prepareStatement(INSERT_BLOCK)) 
        {
            for (int position = 0; position < blocks.size(); position++) 
            {
                rowMapper.bindBlock(insert, idea.id(), position, blocks.get(position));
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void rollback(Connection connection, IdeaId id, SQLException cause) 
    {
        try 
        {
            connection.rollback();
        } 
        catch (SQLException rollbackFailure) 
        {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommitBefore) 
    {
        try 
        {
            connection.setAutoCommit(autoCommitBefore);
        } 
        catch (SQLException e) 
        {
            throw new StorageException("Could not restore auto-commit on the database connection", e);
        }
    }

    @Override
    public Optional<Idea> findById(IdeaId id) 
    {
        Objects.requireNonNull(id, "id");
        Connection connection = connectionSource.connection();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_IDEA)) 
        {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) 
            {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(rowMapper.toIdea(rows, tagsOf(connection, id), blocksOf(connection, id)));
            }
        } 
        catch (SQLException e) 
        {
            throw new StorageException("Could not load idea " + id, e);
        }
    }

    private Set<Tag> tagsOf(Connection connection, IdeaId id) throws SQLException 
    {
        Set<Tag> tags = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_TAGS)) 
        {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) 
            {
                while (rows.next()) 
                {
                    tags.add(rowMapper.toTag(rows));
                }
            }
        }
        return tags;
    }

    private List<ContentBlock> blocksOf(Connection connection, IdeaId id) throws SQLException 
    {
        List<ContentBlock> blocks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BLOCKS)) 
        {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) 
            {
                while (rows.next()) 
                {
                    blocks.add(rowMapper.toBlock(rows));
                }
            }
        }
        return blocks;
    }

    @Override
    public List<Idea> findAll() 
    {
        Connection connection = connectionSource.connection();
        try {
            Map<String, Set<Tag>> tagsByIdea = allTags(connection);
            Map<String, List<ContentBlock>> blocksByIdea = allBlocks(connection);
            List<Idea> ideas = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_IDEAS);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String id = rows.getString("id");
                    ideas.add(rowMapper.toIdea(
                            rows,
                            tagsByIdea.getOrDefault(id, Set.of()),
                            blocksByIdea.getOrDefault(id, List.of())));
                }
            }
            return List.copyOf(ideas);
        } catch (SQLException e) {
            throw new StorageException("Could not load ideas", e);
        }
    }

    private Map<String, Set<Tag>> allTags(Connection connection) throws SQLException {
        Map<String, Set<Tag>> tagsByIdea = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_TAGS);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                tagsByIdea.computeIfAbsent(rows.getString("idea_id"), key -> new LinkedHashSet<>())
                        .add(rowMapper.toTag(rows));
            }
        }
        return tagsByIdea;
    }

    private Map<String, List<ContentBlock>> allBlocks(Connection connection) throws SQLException {
        Map<String, List<ContentBlock>> blocksByIdea = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL_BLOCKS);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                blocksByIdea.computeIfAbsent(rows.getString("idea_id"), key -> new ArrayList<>())
                        .add(rowMapper.toBlock(rows));
            }
        }
        return blocksByIdea;
    }

    @Override
    public boolean delete(IdeaId id) {
        Objects.requireNonNull(id, "id");
        try (PreparedStatement statement = connectionSource.connection().prepareStatement(DELETE_IDEA)) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new StorageException("Could not delete idea " + id, e);
        }
    }

    @Override
    public long count() {
        try (PreparedStatement statement = connectionSource.connection().prepareStatement(COUNT_IDEAS);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new StorageException("Could not count ideas", e);
        }
    }
}
