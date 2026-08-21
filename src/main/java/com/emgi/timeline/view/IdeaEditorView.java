package com.emgi.timeline.view;

import com.emgi.timeline.controller.BlockDraft;
import com.emgi.timeline.controller.BlockKind;
import com.emgi.timeline.controller.IdeaEditorController;
import com.emgi.timeline.controller.IdeaEditorController.SaveResult;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.view.content.BlockEditorFactory;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IdeaEditorView
{
    private static final String ERROR_PREFIX = "⚠ ";

    private final IdeaEditorController controller;

    private Stage stage;

    private BlockEditorFactory blockEditors;

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
    private ScrollPane blockScroll;

    @FXML
    private VBox blockList;

    @FXML
    private Button addTextButton;

    @FXML
    private Button addLinkButton;

    @FXML
    private Button addImageButton;

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
        if(titleField == null || tagPane == null || statusBox == null || blockScroll == null
            || blockList == null || addTextButton == null || addLinkButton == null
            || addImageButton == null || saveButton == null || cancelButton == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }

        titleField.textProperty().bindBidirectional(controller.titleProperty());

        bindError(titleErrorLabel, controller.titleErrorProperty());
        bindError(tagsErrorLabel, controller.tagsErrorProperty());
        bindError(descriptionErrorLabel, controller.descriptionErrorProperty());

        buildStatusControls();
        buildBlockEditor();

        renderTags();
        controller.tags().addListener((ListChangeListener<Tag>) change -> renderTags());

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

    private void buildBlockEditor()
    {
        blockEditors = new BlockEditorFactory(controller);

        rebuildBlocks();
        controller.blocks().addListener((ListChangeListener<BlockDraft>) change -> rebuildBlocks());

        addTextButton.setOnAction(event -> addBlock(BlockKind.TEXT));
        addLinkButton.setOnAction(event -> addBlock(BlockKind.LINK));
        addImageButton.setOnAction(event -> addBlock(BlockKind.IMAGE));
    }

    private void rebuildBlocks()
    {
        List<Node> rows = new ArrayList<>(controller.blocks().size());
        int count = controller.blocks().size();

        for(int i = 0; i < count; i++)
        {
            rows.add(blockEditors.create(controller.blocks().get(i), i == 0, i == count - 1));
        }

        blockList.getChildren().setAll(rows);
    }

    private void addBlock(BlockKind kind)
    {
        controller.addBlock(kind);
        blockScroll.setVvalue(1.0);
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

            case INVALID -> titleField.requestFocus();

            case MISSING -> showMissing();
        }
    }

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
