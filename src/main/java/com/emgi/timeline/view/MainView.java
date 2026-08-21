package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.domain.query.SortOrder;
import com.emgi.timeline.view.cell.IdeaListCell;
import com.emgi.timeline.view.content.BlockRenderer;
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
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MainView
{
    private static final ButtonType DELETE =
            new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);

    private final IdeaListController controller;
    private final IdeaDateFormatter dateFormatter;
    private final IdeaEditorDialog editorDialog;
    private final BlockRenderer blockRenderer;

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

    @FXML
    private StackPane detailRegion;

    @FXML
    private VBox detailPlaceholder;

    @FXML
    private VBox detailPane;

    @FXML
    private Label detailTitle;

    @FXML
    private Label detailMeta;

    @FXML
    private FlowPane detailTags;

    @FXML
    private ScrollPane detailScroll;

    @FXML
    private VBox detailBlocks;

    @FXML
    private Button detailEditButton;

    private final ToggleButton allTagsChip = new ToggleButton("All");

    public MainView(IdeaListController controller,
                    IdeaDateFormatter dateFormatter,
                    IdeaEditorDialog editorDialog,
                    BlockRenderer blockRenderer)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.dateFormatter = Objects.requireNonNull(dateFormatter, "dateFormatter");
        this.editorDialog = Objects.requireNonNull(editorDialog, "editorDialog");
        this.blockRenderer = Objects.requireNonNull(blockRenderer, "blockRenderer");
    }

    @FXML
    private void initialize()
    {
        if(ideaListView == null || emptyState == null || newIdeaButton == null
            || searchField == null || sortChoice == null || tagFilterRow == null
            || tagFilterPane == null || noMatchesState == null || clearFiltersButton == null
            || detailRegion == null || detailPlaceholder == null || detailPane == null
            || detailTitle == null || detailMeta == null || detailTags == null
            || detailScroll == null || detailBlocks == null || detailEditButton == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }

        ideaListView.setItems(controller.ideas());
        ideaListView.setCellFactory(
            list -> new IdeaListCell(dateFormatter, this::editIdea, this::deleteIdea));

        ideaListView.setPlaceholder(new Region());

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
        buildDetailPane(noIdeasAtAll);

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

    private void buildFilterControls()
    {
        searchField.textProperty().bindBidirectional(controller.searchTextProperty());

        sortChoice.getItems().setAll(SortOrder.values());
        sortChoice.setConverter(new StringConverter<SortOrder>()
        {
            @Override
            public String toString(SortOrder order)
            {
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
            syncChipSelection();
        });

        tagFilterRow.visibleProperty().bind(Bindings.isNotEmpty(controller.availableTags()));
        tagFilterRow.managedProperty().bind(tagFilterRow.visibleProperty());

        controller.availableTags().addListener(
            (ListChangeListener<Tag>) change -> rebuildTagChips());
        controller.selectedTags().addListener(
            (ListChangeListener<Tag>) change -> syncChipSelection());

        rebuildTagChips();
    }

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

    private void buildDetailPane(BooleanBinding noIdeasAtAll)
    {
        detailRegion.visibleProperty().bind(noIdeasAtAll.not());
        detailRegion.managedProperty().bind(detailRegion.visibleProperty());

        BooleanBinding nothingSelected = controller.selectedIdeaProperty().isNull();

        detailPlaceholder.visibleProperty().bind(nothingSelected);
        detailPlaceholder.managedProperty().bind(detailPlaceholder.visibleProperty());

        detailPane.visibleProperty().bind(nothingSelected.not());
        detailPane.managedProperty().bind(detailPane.visibleProperty());

        ideaListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, previous, current) -> controller.select(current));

        controller.selectedIdeaProperty().addListener(
            (observable, previous, current) ->
            {
                showDetail(current);

                if(current != null && ideaListView.getSelectionModel().getSelectedItem() != current)
                {
                    ideaListView.getSelectionModel().select(current);
                }
            });

        detailEditButton.setOnAction(event -> editIdea(controller.selectedIdeaProperty().get()));

        showDetail(controller.selectedIdeaProperty().get());
    }

    private void showDetail(Idea idea)
    {
        if(idea == null)
        {
            detailBlocks.getChildren().clear();
            detailTags.getChildren().clear();
            return;
        }

        detailTitle.setText(idea.title());
        detailMeta.setText(dateFormatter.format(idea.createdAt())
            + " \u00b7 " + idea.status().displayName());

        List<Tag> ordered = new ArrayList<>(idea.tags());
        ordered.sort(Comparator.comparing(Tag::name));

        List<Node> chips = new ArrayList<>(ordered.size());
        for(Tag tag : ordered)
        {
            Label chip = new Label(tag.name());
            chip.getStyleClass().add("tag-chip");
            chips.add(chip);
        }
        detailTags.getChildren().setAll(chips);

        detailBlocks.getChildren().setAll(blockRenderer.renderAll(idea.description()));
        detailScroll.setVvalue(0);
    }

    private void createIdea()
    {
        IdeaEditorDialog.Result result = editorDialog.showCreate(window());

        result.saved().ifPresent(idea ->
        {
            controller.add(idea);

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

    private Window window()
    {
        return ideaListView.getScene() == null ? null : ideaListView.getScene().getWindow();
    }
}
