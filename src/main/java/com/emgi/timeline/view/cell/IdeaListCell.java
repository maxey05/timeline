package com.emgi.timeline.view.cell;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.view.format.IdeaDateFormatter;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * One row of the idea list (ARCHITECTURE.md §6.2). Renders; decides nothing.
 *
 * <p>{@code ListCell} instances are recycled — the object that draws row 4 draws row 40 after a
 * scroll — so every node is built once in the constructor and {@link #updateItem} only ever sets
 * values on nodes that already exist.
 *
 * <p>Phase 4 added the Edit/Delete context menu. It lives on the cell rather than on the
 * {@code ListView} because a right-click does not move JavaFX's selection: a menu owned by the
 * cell acts on {@link #getItem()} and needs no selection at all. The cell still decides nothing —
 * it forwards the item to callbacks the view supplied.
 */
public final class IdeaListCell extends ListCell<Idea>
{
    /** Preview length from §6.2. */
    public static final int PREVIEW_CHARS = 120;

    private final IdeaDateFormatter dateFormatter;
    private final ContextMenu contextMenu;

    private final VBox root = new VBox();
    private final HBox topRow = new HBox();
    private final Label titleLabel = new Label();
    private final Label dateLabel = new Label();
    private final Label previewLabel = new Label();
    private final HBox bottomRow = new HBox();
    private final FlowPane tagPane = new FlowPane();
    private final Label statusLabel = new Label();

    public IdeaListCell(IdeaDateFormatter dateFormatter,
                        Consumer<Idea> onEdit,
                        Consumer<Idea> onDelete)
    {
        this.dateFormatter = Objects.requireNonNull(dateFormatter, "dateFormatter");
        Objects.requireNonNull(onEdit, "onEdit");
        Objects.requireNonNull(onDelete, "onDelete");

        MenuItem edit = new MenuItem("Edit…");
        edit.setOnAction(event -> onEdit.accept(getItem()));

        MenuItem delete = new MenuItem("Delete…");
        delete.setOnAction(event -> onDelete.accept(getItem()));

        this.contextMenu = new ContextMenu(edit, delete);

        getStyleClass().add("idea-cell");
        titleLabel.getStyleClass().add("idea-title");
        dateLabel.getStyleClass().add("idea-date");
        previewLabel.getStyleClass().add("idea-preview");
        statusLabel.getStyleClass().add("idea-status");
        tagPane.getStyleClass().add("idea-tags");
        root.getStyleClass().add("idea-cell-root");

        // Title takes the slack, date is pushed to the right edge and never shrinks.
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        dateLabel.setMinWidth(Region.USE_PREF_SIZE);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(titleLabel, dateLabel);

        previewLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        previewLabel.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.getChildren().addAll(tagPane, spacer, statusLabel);

        root.getChildren().addAll(topRow, previewLabel, bottomRow);

        // Without this the ListView sizes itself to its widest cell and grows a horizontal
        // scrollbar the first time someone writes a long title.
        setPrefWidth(0);
        setMaxWidth(Double.MAX_VALUE);
    }

    @Override
    protected void updateItem(Idea idea, boolean empty)
    {
        super.updateItem(idea, empty);

        if(empty || idea == null)
        {
            // Skipping this leaves ghost rows below the last real one.
            setGraphic(null);
            setText(null);
            // An empty row below the last idea has nothing to edit or delete.
            setContextMenu(null);
            return;
        }

        titleLabel.setText(idea.title());

        // createdAt, not updatedAt: §6.2's column is the idea's age.
        dateLabel.setText(dateFormatter.format(idea.createdAt()));

        String preview = idea.description().plainTextPreview(PREVIEW_CHARS);
        previewLabel.setText(preview);
        // Invisible is not enough — an unmanaged node is what stops an idea with no description
        // from leaving a dead band of whitespace in its row.
        previewLabel.setVisible(!preview.isEmpty());
        previewLabel.setManaged(!preview.isEmpty());

        tagPane.getChildren().setAll(chipsFor(idea));

        statusLabel.setText(glyphFor(idea.status()) + " " + idea.status().displayName());

        // Both. Leaving the text set draws the record's toString() behind the layout.
        setGraphic(root);
        setText(null);
        setContextMenu(contextMenu);
    }

    /**
     * A chip per tag, in a stable order. {@code Set<Tag>} iteration order is arbitrary, so
     * unsorted tags would render differently between launches for the same idea.
     *
     * <p>This is the one per-update allocation in the cell, and it is bounded:
     * {@code IdeaValidator.MAX_TAGS} caps an idea at 20 tags.
     */
    private static List<Label> chipsFor(Idea idea)
    {
        List<Tag> ordered = new ArrayList<>(idea.tags());
        ordered.sort(Comparator.comparing(Tag::name));

        List<Label> chips = new ArrayList<>(ordered.size());
        for(Tag tag : ordered)
        {
            Label chip = new Label(tag.name());
            chip.getStyleClass().add("tag-chip");
            chips.add(chip);
        }

        return chips;
    }

    /**
     * The glyph is presentation, so it is chosen here; the words are not, so they come from
     * {@link IdeaStatus#displayName()}.
     *
     * <p>No {@code default} branch, for the same reason §7.5's {@code BlockRenderer} has none: a
     * fourth status should break the build here rather than render a blank glyph.
     */
    private static String glyphFor(IdeaStatus status)
    {
        return switch(status)
        {
            case INCOMPLETE -> "○";
            case IN_PROGRESS -> "◐";
            case COMPLETED -> "●";
        };
    }
}
