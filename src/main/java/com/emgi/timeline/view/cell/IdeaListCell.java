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

public final class IdeaListCell extends ListCell<Idea>
{
    public static final int PREVIEW_CHARS = 120;

    /** What the preview line says for an idea whose description is nothing but pictures. */
    public static final String IMAGES_ONLY_PREVIEW = "Attached images";

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

        setPrefWidth(0);
        setMaxWidth(Double.MAX_VALUE);
    }

    @Override
    protected void updateItem(Idea idea, boolean empty)
    {
        super.updateItem(idea, empty);

        if(empty || idea == null)
        {
            setGraphic(null);
            setText(null);
            setContextMenu(null);
            return;
        }

        titleLabel.setText(idea.title());

        dateLabel.setText(dateFormatter.format(idea.createdAt()));

        String preview = idea.description().plainTextPreview(PREVIEW_CHARS);

        /*
         * A description made only of images previews as nothing, because the preview drops
         * image tokens rather than showing a file name nobody can read anything from. Saying
         * so is better than a row that looks like it has no description at all.
         */
        if(preview.isEmpty() && idea.description().hasOnlyImages())
        {
            preview = IMAGES_ONLY_PREVIEW;
        }

        previewLabel.setText(preview);
        previewLabel.setVisible(!preview.isEmpty());
        previewLabel.setManaged(!preview.isEmpty());

        tagPane.getChildren().setAll(chipsFor(idea));

        statusLabel.setText(glyphFor(idea.status()) + " " + idea.status().displayName());

        setGraphic(root);
        setText(null);
        setContextMenu(contextMenu);
    }

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
