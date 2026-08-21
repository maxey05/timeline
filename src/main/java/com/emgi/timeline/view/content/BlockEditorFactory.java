package com.emgi.timeline.view.content;

import com.emgi.timeline.controller.BlockDraft;
import com.emgi.timeline.controller.BlockKind;
import com.emgi.timeline.controller.IdeaEditorController;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

public final class BlockEditorFactory
{
    private static final String GLYPH_TEXT = "\u00b6";
    private static final String GLYPH_LINK = "\u2197";
    private static final String GLYPH_IMAGE = "\u25a3";

    private static final int TEXT_ROWS = 3;

    public record BlockRow(BlockDraft draft, Node node, Node focusTarget) { }

    private final IdeaEditorController controller;

    public BlockEditorFactory(IdeaEditorController controller)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public BlockRow create(BlockDraft draft, boolean first, boolean last)
    {
        Objects.requireNonNull(draft, "draft");

        Label glyph = new Label(glyphFor(draft.kind()));
        glyph.getStyleClass().add("block-glyph");

        List<Node> inputs = fieldsFor(draft);

        VBox fields = new VBox();
        fields.getStyleClass().add("block-fields");
        fields.getChildren().setAll(inputs);
        HBox.setHgrow(fields, Priority.ALWAYS);

        HBox row = new HBox(glyph, fields, controlsFor(draft, first, last));
        row.getStyleClass().add("block-row");
        row.setAlignment(Pos.TOP_LEFT);

        row.setUserData(draft);

        return new BlockRow(draft, row, inputs.get(0));
    }

    private List<Node> fieldsFor(BlockDraft draft)
    {
        return switch(draft.kind())
        {
            case TEXT -> List.of(textArea(draft));

            case LINK -> List.of(
                field(draft.uriProperty(), "https://\u2026"),
                field(draft.labelProperty(), "link text (optional)"));

            case IMAGE -> List.of(
                field(draft.uriProperty(), "https://\u2026 or file:///C:/\u2026"),
                field(draft.altTextProperty(), "alt text"));
        };
    }

    private static Node textArea(BlockDraft draft)
    {
        TextArea area = new TextArea();
        area.getStyleClass().addAll("text-input", "block-text-input");
        area.setWrapText(true);
        area.setPrefRowCount(TEXT_ROWS);
        area.textProperty().bindBidirectional(draft.textProperty());
        return area;
    }

    private static Node field(StringProperty bound, String prompt)
    {
        TextField input = new TextField();
        input.getStyleClass().addAll("text-input", "block-field");
        input.setPromptText(prompt);
        input.textProperty().bindBidirectional(bound);
        return input;
    }

    private Node controlsFor(BlockDraft draft, boolean first, boolean last)
    {
        Button up = iconButton("\u2191", "Move block up  (Alt+Up)");
        up.setDisable(first);
        up.setOnAction(event -> controller.moveBlockUp(draft));

        Button down = iconButton("\u2193", "Move block down  (Alt+Down)");
        down.setDisable(last);
        down.setOnAction(event -> controller.moveBlockDown(draft));

        Button remove = iconButton("\u2715", "Remove block");
        remove.setOnAction(event -> controller.removeBlock(draft));

        HBox controls = new HBox(up, down, remove);
        controls.getStyleClass().add("block-controls");
        controls.setAlignment(Pos.TOP_RIGHT);
        return controls;
    }

    private static Button iconButton(String glyph, String tooltip)
    {
        Button button = new Button(glyph);
        button.getStyleClass().add("block-button");
        button.setTooltip(new Tooltip(tooltip));

        button.setAccessibleText(tooltip);

        return button;
    }

    private static String glyphFor(BlockKind kind)
    {
        return switch(kind)
        {
            case TEXT -> GLYPH_TEXT;
            case LINK -> GLYPH_LINK;
            case IMAGE -> GLYPH_IMAGE;
        };
    }
}
