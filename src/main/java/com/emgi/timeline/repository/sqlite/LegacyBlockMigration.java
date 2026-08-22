package com.emgi.timeline.repository.sqlite;

import com.emgi.timeline.domain.content.DescriptionParser;
import com.emgi.timeline.repository.StorageException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds the old {@code idea_block} table into the new {@code idea.description} column.
 *
 * <p>This runs once, on a database written by a build that still had the block model, and
 * then the table is gone and it never runs again. It is intentionally the only place in
 * the codebase that still knows the words TEXT, LINK and IMAGE as block types.
 *
 * <p>The flattening is lossy in exactly one way, and knowingly: a link block carried a
 * separate label, and the new format has nowhere to put one, because links are recognised
 * from the address itself. A labelled link becomes {@code label (address)}, which reads
 * naturally and still renders the address as a link.
 */
final class LegacyBlockMigration {

    private static final String LEGACY_TABLE = "idea_block";

    private static final String SELECT_BLOCKS =
            "SELECT idea_id, type, text, uri, label, alt_text FROM idea_block ORDER BY idea_id, position";

    private static final String UPDATE_DESCRIPTION =
            "UPDATE idea SET description = ? WHERE id = ?";

    private LegacyBlockMigration() {
    }

    /** Adds the description column if it is missing, then drains and drops the block table. */
    static void apply(Connection connection) {
        try {
            if (!hasColumn(connection, "idea", "description")) {
                execute(connection,
                        "ALTER TABLE idea ADD COLUMN description TEXT NOT NULL DEFAULT ''");
            }

            if (!hasTable(connection, LEGACY_TABLE)) {
                return;
            }

            writeDescriptions(connection, flatten(connection));
            execute(connection, "DROP TABLE " + LEGACY_TABLE);
        } catch (SQLException e) {
            throw new StorageException("Could not migrate the old description blocks", e);
        }
    }

    private static Map<String, String> flatten(Connection connection) throws SQLException {
        Map<String, List<String>> partsByIdea = new LinkedHashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(SELECT_BLOCKS);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String part = partFor(rows);
                if (!part.isBlank()) {
                    partsByIdea.computeIfAbsent(rows.getString("idea_id"), key -> new ArrayList<>())
                            .add(part);
                }
            }
        }

        Map<String, String> descriptions = new LinkedHashMap<>();
        partsByIdea.forEach((ideaId, parts) -> descriptions.put(ideaId, String.join("\n\n", parts)));
        return descriptions;
    }

    private static String partFor(ResultSet row) throws SQLException {
        String type = row.getString("type");

        return switch (type == null ? "" : type) {
            case "TEXT" -> value(row.getString("text"));
            case "LINK" -> linkText(value(row.getString("uri")), value(row.getString("label")));
            case "IMAGE" -> imageText(value(row.getString("uri")), value(row.getString("alt_text")));
            default -> "";
        };
    }

    private static String linkText(String uri, String label) {
        if (uri.isBlank()) {
            return label;
        }
        return label.isBlank() || label.equals(uri) ? uri : label + " (" + uri + ")";
    }

    private static String imageText(String uri, String altText) {
        if (uri.isBlank()) {
            return "";
        }

        try {
            return DescriptionParser.imageToken(new URI(uri), altText);
        } catch (URISyntaxException e) {
            return altText;
        }
    }

    private static void writeDescriptions(Connection connection, Map<String, String> descriptions)
            throws SQLException {
        if (descriptions.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(UPDATE_DESCRIPTION)) {
            for (Map.Entry<String, String> entry : descriptions.entrySet()) {
                statement.setString(1, entry.getValue());
                statement.setString(2, entry.getKey());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static boolean hasTable(Connection connection, String table) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(null, null, table, null)) {
            return tables.next();
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String value(String raw) {
        return raw == null ? "" : raw;
    }
}
