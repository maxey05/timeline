package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.view.cell.IdeaListCell;
import com.emgi.timeline.view.format.IdeaDateFormatter;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * The {@code fx:controller} for MainView.fxml — a view backing class, not an MVC controller
 * (ARCHITECTURE.md §2). It binds nodes to state and forwards gestures. It contains no rules.
 */
public class MainView
{
    private final IdeaListController controller;
    private final IdeaDateFormatter dateFormatter;

    @FXML
    private ListView<Idea> ideaListView;

    @FXML
    private VBox emptyState;

    @FXML
    private Button newIdeaButton;

    public MainView(IdeaListController controller, IdeaDateFormatter dateFormatter)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.dateFormatter = Objects.requireNonNull(dateFormatter, "dateFormatter");
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
        ideaListView.setCellFactory(list -> new IdeaListCell(dateFormatter));

        // ListView's default placeholder is the string "No content in table", which would flash
        // behind the empty state below.
        ideaListView.setPlaceholder(new Region());

        // The empty state and the list are mutually exclusive. Bind both visible AND managed: an
        // invisible-but-managed node still occupies layout space.
        emptyState.visibleProperty().bind(Bindings.isEmpty(controller.ideas()));
        emptyState.managedProperty().bind(emptyState.visibleProperty());
        ideaListView.visibleProperty().bind(Bindings.isNotEmpty(controller.ideas()));
        ideaListView.managedProperty().bind(ideaListView.visibleProperty());

        // Phase 4 owns this button. Until then it is honest about doing nothing.
        newIdeaButton.setDisable(true);
    }
}
