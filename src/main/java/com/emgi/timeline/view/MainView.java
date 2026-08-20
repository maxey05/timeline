package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.domain.query.SortOrder;
import com.emgi.timeline.view.cell.IdeaListCell;
import com.emgi.timeline.view.format.IdeaDateFormatter;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
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

    @FXML
    private TextField searchField;

    @FXML
    private ChoiceBox<SortOrder> sortChoice;

    @FXML
    private HBox tagFilterRow;

    @FXML
    private FlowPane tagFilterPane;

    @FXML
    private VBox noMatchesState;

    @FXML
    private Button clearFiltersButton;

    /**
     * Not a tag, and not in a {@code ToggleGroup}: it is selected exactly when no tag is, and
     * clicking it clears the tag selection. A group would enforce single selection, which is the
     * opposite of the OR filter locked decision #4 asks for.
     */
    private final ToggleButton allTagsChip = new ToggleButton("All");

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
        if(ideaListView == null || emptyState == null || newIdeaButton == null
            || searchField == null || sortChoice == null || tagFilterRow == null
            || tagFilterPane == null || noMatchesState == null || clearFiltersButton == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }

        ideaListView.setItems(controller.ideas());
        ideaListView.setCellFactory(
            list -> new IdeaListCell(dateFormatter, this::editIdea, this::deleteIdea));

        // ListView's default placeholder is the string "No content in table", which would flash
        // behind the empty states below.
        ideaListView.setPlaceholder(new Region());

        // Three mutually exclusive center states. "No ideas yet" is about the database; "no
        // matches" is about the query — telling someone with 200 ideas that they have none is the
        // bug this split exists to prevent. Bind both visible AND managed: an invisible but
        // managed node still occupies layout space.
        BooleanBinding noIdeasAtAll = Bindings.isEmpty(controller.allIdeas());
        BooleanBinding nothingVisible = Bindings.isEmpty(controller.ideas());

        emptyState.visibleProperty().bind(noIdeasAtAll);
        emptyState.managedProperty().bind(emptyState.visibleProperty());

        noMatchesState.visibleProperty().bind(noIdeasAtAll.not().and(nothingVisible));
        noMatchesState.managedProperty().bind(noMatchesState.visibleProperty());

        ideaListView.visibleProperty().bind(nothingVisible.not());
        ideaListView.managedProperty().bind(ideaListView.visibleProperty());

        clearFiltersButton.setOnAction(event -> controller.clearFilters());

        newIdeaButton.setDisable(false);
        newIdeaButton.setOnAction(event -> createIdea());

        buildFilterControls();

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

    /** Search box, sort control, and the tag chip row (§6.4). */
    private void buildFilterControls()
    {
        // Bidirectional: clearFilters() must empty the box on screen, not just in the model.
        searchField.textProperty().bindBidirectional(controller.searchTextProperty());

        // Items before the binding, so the initial value has something to resolve against.
        sortChoice.getItems().setAll(SortOrder.values());
        sortChoice.setConverter(new StringConverter<SortOrder>()
        {
            @Override
            public String toString(SortOrder order)
            {
                // The labels live on the enum (§10) — never a switch here, never a literal.
                return order == null ? "" : order.displayName();
            }

            @Override
            public SortOrder fromString(String text)
            {
                throw new UnsupportedOperationException("SortOrder is chosen, never typed");
            }
        });
        sortChoice.valueProperty().bindBidirectional(controller.sortOrderProperty());

        allTagsChip.getStyleClass().add("filter-chip");
        allTagsChip.setOnAction(event ->
        {
            controller.selectedTags().clear();
            // clear() on an already-empty list fires no change event, so without this line a
            // click on an already-selected "All" would leave it drawn as unselected.
            syncChipSelection();
        });

        tagFilterRow.visibleProperty().bind(Bindings.isNotEmpty(controller.availableTags()));
        tagFilterRow.managedProperty().bind(tagFilterRow.visibleProperty());

        controller.availableTags().addListener(
            (ListChangeListener<Tag>) change -> rebuildTagChips());
        controller.selectedTags().addListener(
            (ListChangeListener<Tag>) change -> syncChipSelection());

        // App.start() calls load() before FXMLLoader.load(), so there is already data to draw.
        rebuildTagChips();
    }

    /**
     * Rebuilds the chip row from the controller's tag list. Chips are cheap and few — the tag
     * vocabulary of a personal idea list is tens of entries — so this rebuilds wholesale rather
     * than diffing, which is why the controller only republishes the list when it truly changed.
     */
    private void rebuildTagChips()
    {
        List<Node> chips = new ArrayList<>();
        chips.add(allTagsChip);

        for(Tag tag : controller.availableTags())
        {
            ToggleButton chip = new ToggleButton(tag.name());
            chip.getStyleClass().add("filter-chip");
            chip.setUserData(tag);
            chip.setOnAction(event -> controller.toggleTag(tag));
            chips.add(chip);
        }

        tagFilterPane.getChildren().setAll(chips);
        syncChipSelection();
    }

    /**
     * Pushes the controller's selection onto the chips. {@code setSelected} does not fire an
     * {@code ActionEvent} — only a real click does — so this cannot loop back into
     * {@code toggleTag}.
     */
    private void syncChipSelection()
    {
        allTagsChip.setSelected(controller.selectedTags().isEmpty());

        for(Node node : tagFilterPane.getChildren())
        {
            if(node == allTagsChip)
            {
                continue;
            }

            ToggleButton chip = (ToggleButton) node;
            chip.setSelected(controller.selectedTags().contains((Tag) chip.getUserData()));
        }
    }

    private void createIdea()
    {
        IdeaEditorDialog.Result result = editorDialog.showCreate(window());

        result.saved().ifPresent(idea ->
        {
            controller.add(idea);

            // The active filter may exclude what was just created — a new idea shares no tag with
            // a tag filter, or its title misses the search term. Selecting a row that is not in
            // the visible list is a silent no-op, so ask first.
            if(controller.ideas().contains(idea))
            {
                ideaListView.getSelectionModel().select(idea);
                ideaListView.scrollTo(idea);
            }
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
