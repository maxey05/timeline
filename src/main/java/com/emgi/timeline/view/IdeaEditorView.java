package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaEditorController;
import com.emgi.timeline.controller.IdeaEditorController.SaveResult;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code fx:controller} for IdeaEditorView.fxml — a view backing class, not an MVC controller
 * (ARCHITECTURE.md §2). Every rule it appears to enforce actually lives in
 * {@link IdeaEditorController} or in the domain; this class binds and forwards.
 */
public class IdeaEditorView
{
    /** Prefix on every visible error message. The glyph is presentation, so it is chosen here. */
    private static final String ERROR_PREFIX = "⚠ ";

    private final IdeaEditorController controller;

    /** Set by {@link IdeaEditorDialog} once the scene exists; Save and Cancel close it. */
    private Stage stage;

    @FXML
    private TextField titleField;

    @FXML
    private Label titleErrorLabel;

    @FXML
    private FlowPane tagPane;

    @FXML
    private TextField tagField;

    @FXML
    private Label tagsErrorLabel;

    @FXML
    private HBox statusBox;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label descriptionErrorLabel;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    public IdeaEditorView(IdeaEditorController controller)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    void setStage(Stage stage)
    {
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    @FXML
    private void initialize()
    {
        if(titleField == null || tagPane == null || statusBox == null || descriptionArea == null
            || saveButton == null || cancelButton == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }

        // Bidirectional: the controller owns the value, the field is a window onto it.
        titleField.textProperty().bindBidirectional(controller.titleProperty());
        descriptionArea.textProperty().bindBidirectional(controller.descriptionTextProperty());

        bindError(titleErrorLabel, controller.titleErrorProperty());
        bindError(tagsErrorLabel, controller.tagsErrorProperty());
        bindError(descriptionErrorLabel, controller.descriptionErrorProperty());

        buildStatusControls();

        renderTags();
        controller.tags().addListener((ListChangeListener<Tag>) change -> renderTags());

        // Enter in the tag field commits one tag. The field keeps focus so several can be typed
        // in a row; it is only cleared when the tag was accepted.
        tagField.setOnAction(event ->
        {
            if(controller.addTag(tagField.getText()))
            {
                tagField.clear();
            }
        });

        saveButton.setOnAction(event -> onSave());
        cancelButton.setOnAction(event -> stage.close());
    }

    /**
     * §6.3: the error slot keeps its space permanently. Bind {@code visible} and <em>not</em>
     * {@code managed} — an invisible-but-managed node still occupies layout, which is exactly what
     * stops the form jumping when a message appears. (The Phase 3 list cell does the opposite with
     * its preview label, on purpose: there, the row should collapse.)
     */
    private static void bindError(Label label, ObservableValue<String> message)
    {
        label.textProperty().bind(Bindings.createStringBinding(
            () -> isBlank(message.getValue()) ? "" : ERROR_PREFIX + message.getValue(),
            message));

        label.visibleProperty().bind(Bindings.createBooleanBinding(
            () -> !isBlank(message.getValue()),
            message));
    }

    private static boolean isBlank(String text)
    {
        return text == null || text.isEmpty();
    }

    /** Built from the enum, labelled from {@code displayName()} — §10, never hardcoded strings. */
    private void buildStatusControls()
    {
        ToggleGroup group = new ToggleGroup();

        for(IdeaStatus value : IdeaStatus.values())
        {
            RadioButton button = new RadioButton(value.displayName());
            button.getStyleClass().add("status-option");
            button.setToggleGroup(group);
            button.setUserData(value);
            button.setSelected(value == controller.statusProperty().get());
            statusBox.getChildren().add(button);
        }

        group.selectedToggleProperty().addListener((observable, previous, current) ->
        {
            if(current != null)
            {
                controller.statusProperty().set((IdeaStatus) current.getUserData());
            }
        });
    }

    private void renderTags()
    {
        List<Node> chips = new ArrayList<>(controller.tags().size());

        for(Tag tag : controller.tags())
        {
            chips.add(chipFor(tag));
        }

        tagPane.getChildren().setAll(chips);
    }

    private Node chipFor(Tag tag)
    {
        Label name = new Label(tag.name());

        Button remove = new Button("×");
        remove.getStyleClass().add("tag-remove");
        remove.setOnAction(event -> controller.removeTag(tag));

        HBox chip = new HBox(name, remove);
        chip.getStyleClass().add("tag-chip");
        chip.setAlignment(Pos.CENTER_LEFT);

        return chip;
    }

    private void onSave()
    {
        SaveResult result = controller.save();

        switch(result)
        {
            case SAVED -> stage.close();

            // Nothing else to do: the error properties are bound, so the messages are already on
            // screen. Staying open is the entire point. Focus goes back to the first field so the
            // fix is one keystroke away.
            case INVALID -> titleField.requestFocus();

            case MISSING -> showMissing();
        }
    }

    /**
     * Unreachable in V1 — the editor is modal, so nothing can delete the idea while it is open —
     * but §7.2's NOT_FOUND branch exists, and a dialog that silently does nothing is worse than a
     * dialog that says what happened.
     */
    private void showMissing()
    {
        Alert alert = new Alert(AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle("Timeline");
        alert.setHeaderText("This idea no longer exists.");
        alert.setContentText("It was deleted somewhere else, so your changes weren't saved.");
        alert.showAndWait();
        stage.close();
    }
}
