package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.view.cell.IdeaListCell;
import com.emgi.timeline.view.format.IdeaDateFormatter;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Objects;
import java.util.Optional;

/**
 * The {@code fx:controller} for MainView.fxml — a view backing class, not an MVC controller
 * (ARCHITECTURE.md §2). It binds nodes to state and forwards gestures. It contains no rules.
 */
public class MainView
{
    private static final ButtonType DELETE =
            new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);

    private final IdeaListController controller;
    private final IdeaDateFormatter dateFormatter;
    private final IdeaEditorDialog editorDialog;

    @FXML
    private ListView<Idea> ideaListView;

    @FXML
    private VBox emptyState;

    @FXML
    private Button newIdeaButton;

    public MainView(IdeaListController controller,
                    IdeaDateFormatter dateFormatter,
                    IdeaEditorDialog editorDialog)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.dateFormatter = Objects.requireNonNull(dateFormatter, "dateFormatter");
        this.editorDialog = Objects.requireNonNull(editorDialog, "editorDialog");
    }

    @FXML
    private void initialize()
    {
        if(ideaListView == null || emptyState == null || newIdeaButton == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }

        ideaListView.setItems(controller.ideas());
        ideaListView.setCellFactory(
            list -> new IdeaListCell(dateFormatter, this::editIdea, this::deleteIdea));

        // ListView's default placeholder is the string "No content in table", which would flash
        // behind the empty state below.
        ideaListView.setPlaceholder(new Region());

        // The empty state and the list are mutually exclusive. Bind both visible AND managed: an
        // invisible-but-managed node still occupies layout space.
        emptyState.visibleProperty().bind(Bindings.isEmpty(controller.ideas()));
        emptyState.managedProperty().bind(emptyState.visibleProperty());
        ideaListView.visibleProperty().bind(Bindings.isNotEmpty(controller.ideas()));
        ideaListView.managedProperty().bind(ideaListView.visibleProperty());

        newIdeaButton.setDisable(false);
        newIdeaButton.setOnAction(event -> createIdea());

        // Double-click opens the selected row. Guarded on a real item, so a double-click on the
        // empty space below the last idea does nothing.
        ideaListView.setOnMouseClicked(event ->
        {
            if(event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2)
            {
                Idea selected = ideaListView.getSelectionModel().getSelectedItem();
                if(selected != null)
                {
                    editIdea(selected);
                }
            }
        });
    }

    private void createIdea()
    {
        IdeaEditorDialog.Result result = editorDialog.showCreate(window());

        result.saved().ifPresent(idea ->
        {
            controller.add(idea);

            // The new idea is the newest, so under the default sort it is at the top — but select
            // and scroll explicitly rather than relying on that, since Phase 5 adds a sort control.
            ideaListView.getSelectionModel().select(idea);
            ideaListView.scrollTo(idea);
        });
    }

    private void editIdea(Idea idea)
    {
        if(idea == null)
        {
            return;
        }

        IdeaEditorDialog.Result result = editorDialog.showEdit(window(), idea);

        result.saved().ifPresent(controller::replace);

        if(result.targetMissing())
        {
            // The row on screen is a ghost — the cheapest correct fix is to re-read storage.
            controller.load();
        }
    }

    private void deleteIdea(Idea idea)
    {
        if(idea == null)
        {
            return;
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.initOwner(window());
        confirm.setTitle("Timeline");
        confirm.setHeaderText("Delete \"" + idea.title() + "\"?");
        confirm.setContentText("This can't be undone.");
        confirm.getButtonTypes().setAll(ButtonType.CANCEL, DELETE);

        // Make Cancel the default, so Enter on a confirmation dialog never destroys anything.
        Button cancelButton = (Button) confirm.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setDefaultButton(true);
        Button deleteButton = (Button) confirm.getDialogPane().lookupButton(DELETE);
        deleteButton.setDefaultButton(false);

        Optional<ButtonType> choice = confirm.showAndWait();
        if(choice.isPresent() && choice.get() == DELETE)
        {
            controller.delete(idea);
        }
    }

    /** The owner for modal children. Available from the moment the scene is shown. */
    private Window window()
    {
        return ideaListView.getScene() == null ? null : ideaListView.getScene().getWindow();
    }
}
