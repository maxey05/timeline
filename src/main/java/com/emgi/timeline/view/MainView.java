package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.domain.query.SortOrder;
import com.emgi.timeline.view.cell.IdeaListCell;
import com.emgi.timeline.view.content.BlockRenderer;
import com.emgi.timeline.view.format.IdeaDateFormatter;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.event.EventTarget;
import javafx.geometry.Side;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
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

    /** How much of the window the detail panel is allowed to cover, and its absolute ceiling. */
    private static final double PANEL_WIDTH_FRACTION = 0.72;
    private static final double PANEL_HEIGHT_FRACTION = 0.80;
    private static final double PANEL_MAX_WIDTH = 720;
    private static final double PANEL_MAX_HEIGHT = 620;

    private final IdeaListController controller;
    private final IdeaDateFormatter dateFormatter;
    private final IdeaEditorDialog editorDialog;
    private final BlockRenderer blockRenderer;

    @FXML
    private BorderPane contentRoot;

    @FXML
    private ListView<Idea> ideaListView;

    @FXML
    private VBox emptyState;

    @FXML
    private Button newIdeaButton;

    @FXML
    private Button settingsButton;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<SortOrder> sortChoice;

    @FXML
    private HBox tagFilterRow;

    @FXML
    private FlowPane tagFilterPane;

    @FXML
    private VBox noMatchesState;

    @FXML
    private Button clearFiltersButton;

    @FXML
    private StackPane detailOverlay;

    @FXML
    private Region detailScrim;

    @FXML
    private VBox detailPanel;

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
    private Button detailCloseButton;

    @FXML
    private Button detailEditButton;

    private final ToggleButton allTagsChip = new ToggleButton("All");
    private final ContextMenu settingsMenu = new ContextMenu();

    /** Tab cycles within these while the panel is open, so focus never escapes behind the scrim. */
    private List<Node> overlayFocusRing = List.of();

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
        if(contentRoot == null || ideaListView == null || emptyState == null || newIdeaButton == null
            || settingsButton == null
            || searchField == null || sortChoice == null || tagFilterRow == null
            || tagFilterPane == null || noMatchesState == null || clearFiltersButton == null
            || detailOverlay == null || detailScrim == null || detailPanel == null
            || detailTitle == null || detailMeta == null || detailTags == null
            || detailScroll == null || detailBlocks == null || detailCloseButton == null
            || detailEditButton == null)
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

        buildSettingsMenu();
        buildFilterControls();
        buildDetailOverlay();
        installKeyboard();

        ideaListView.setOnMouseClicked(this::onListClick);
    }

    private void installKeyboard()
    {
        newIdeaButton.setTooltip(new Tooltip("New idea  (Ctrl+N)"));
        searchField.setTooltip(new Tooltip("Search titles  (Ctrl+F)"));
        detailCloseButton.setTooltip(new Tooltip("Close  (Esc)"));
        detailEditButton.setTooltip(new Tooltip("Edit this idea"));

        ideaListView.setOnKeyPressed(this::onListKey);
        searchField.setOnKeyPressed(this::onSearchKey);

        ideaListView.sceneProperty().addListener((observable, previous, current) ->
        {
            if(current != null)
            {
                installAccelerators(current);
                current.addEventFilter(KeyEvent.KEY_PRESSED, this::onSceneKey);
            }
        });
    }

    private void installAccelerators(Scene scene)
    {
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN),
            this::createIdea);

        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN),
            this::focusSearch);

        Platform.runLater(ideaListView::requestFocus);
    }

    /**
     * While the detail panel is open the window behind it is modal: Esc closes, Tab stays inside
     * the panel, the accelerators are muted, and every other key aimed at something behind the
     * scrim is swallowed. A filter, not a handler — accelerators only see events a filter left
     * unconsumed, which is the only way to mute them.
     */
    private void onSceneKey(KeyEvent event)
    {
        if(!detailOverlay.isVisible())
        {
            return;
        }

        if(event.getCode() == KeyCode.ESCAPE)
        {
            closeDetail();
            event.consume();
            return;
        }

        if(event.getCode() == KeyCode.TAB)
        {
            cycleOverlayFocus(event.isShiftDown());
            event.consume();
            return;
        }

        if(event.isShortcutDown() || !isInsideOverlay(event.getTarget()))
        {
            event.consume();
        }
    }

    private void cycleOverlayFocus(boolean backwards)
    {
        if(overlayFocusRing.isEmpty())
        {
            return;
        }

        Scene scene = detailOverlay.getScene();
        Node focused = scene == null ? null : scene.getFocusOwner();

        // List.of(...).indexOf(null) throws — an immutable list rejects a null probe.
        int current = focused == null ? -1 : overlayFocusRing.indexOf(focused);
        int size = overlayFocusRing.size();
        int next = current < 0 ? 0 : ((current + (backwards ? -1 : 1)) + size) % size;

        overlayFocusRing.get(next).requestFocus();
    }

    private boolean isInsideOverlay(EventTarget target)
    {
        if(!(target instanceof Node node))
        {
            return false;
        }

        for(Node current = node; current != null; current = current.getParent())
        {
            if(current == detailOverlay)
            {
                return true;
            }
        }

        return false;
    }

    private void onListClick(MouseEvent event)
    {
        if(event.getButton() != MouseButton.PRIMARY)
        {
            return;
        }

        ListCell<?> cell = enclosingCell(event.getPickResult().getIntersectedNode());

        if(cell == null || cell.isEmpty())
        {
            return;
        }

        openDetail();
    }

    private static ListCell<?> enclosingCell(Node node)
    {
        for(Node current = node; current != null; current = current.getParent())
        {
            if(current instanceof ListCell<?> cell)
            {
                return cell;
            }
        }

        return null;
    }

    private void onListKey(KeyEvent event)
    {
        Idea selected = ideaListView.getSelectionModel().getSelectedItem();

        if(selected == null)
        {
            return;
        }

        switch(event.getCode())
        {
            case ENTER ->
            {
                openDetail();
                event.consume();
            }
            case DELETE ->
            {
                deleteIdea(selected);
                event.consume();
            }
            default ->
            {
            }
        }
    }

    private void onSearchKey(KeyEvent event)
    {
        if(event.getCode() == KeyCode.ESCAPE && !searchField.getText().isEmpty())
        {
            searchField.clear();
            event.consume();
        }
    }

    private void focusSearch()
    {
        searchField.requestFocus();
        searchField.selectAll();
    }

    /**
     * The gear beside "+ New Idea".
     *
     * Side.BOTTOM anchors the menu under the button instead of over it, and the 4px gap keeps
     * the button's own border visible while the menu is open. The menu is then nudged left so
     * its right edge lines up with the button's — its width is only known once it is showing,
     * which is why that cannot be an offset passed to show().
     */
    private void buildSettingsMenu()
    {
        settingsButton.setAccessibleText("Settings");
        settingsButton.setTooltip(new Tooltip("Settings"));

        // TODO: replace this placeholder with the real entries once the settings surface is
        //       decided. Candidates raised so far: Appearance, Storage location, About Timeline.
        //       Note that window geometry lives in Preferences while everything else is in
        //       SQLite, so "where settings are stored" is an open question of its own.
        MenuItem placeholder = new MenuItem("No settings yet");
        placeholder.setDisable(true);
        settingsMenu.getItems().setAll(placeholder);

        settingsMenu.setOnShowing(event -> settingsButton.getStyleClass().add("showing"));
        settingsMenu.setOnHidden(event -> settingsButton.getStyleClass().remove("showing"));

        settingsButton.setOnAction(event -> toggleSettingsMenu());
    }

    private void toggleSettingsMenu()
    {
        if(settingsMenu.isShowing())
        {
            settingsMenu.hide();
            return;
        }

        settingsMenu.show(settingsButton, Side.BOTTOM, 0, 4);
        settingsMenu.setX(settingsMenu.getX() + settingsButton.getWidth() - settingsMenu.getWidth());
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

    private void buildDetailOverlay()
    {
        detailOverlay.setVisible(false);
        detailOverlay.managedProperty().bind(detailOverlay.visibleProperty());

        /*
         * The panel is a fraction of the window rather than a fixed box, so the dimmed margin
         * around it stays visible at every window size, and it still stops growing on a big
         * monitor where a 1400px-wide read pane would be unreadable.
         */
        detailPanel.maxWidthProperty().bind(Bindings.min(
            contentRoot.widthProperty().multiply(PANEL_WIDTH_FRACTION), PANEL_MAX_WIDTH));
        detailPanel.maxHeightProperty().bind(Bindings.min(
            contentRoot.heightProperty().multiply(PANEL_HEIGHT_FRACTION), PANEL_MAX_HEIGHT));

        overlayFocusRing = List.of(detailScroll, detailEditButton, detailCloseButton);

        detailScrim.setOnMouseClicked(event ->
        {
            /*
             * Only a single click dismisses. A double click on a list row opens the panel on the
             * first press, which puts the scrim under the second press — without this guard the
             * panel would flash open and shut again.
             */
            if(event.getClickCount() == 1)
            {
                closeDetail();
            }

            event.consume();
        });

        detailCloseButton.setOnAction(event -> closeDetail());
        detailEditButton.setOnAction(event -> editIdea(controller.selectedIdeaProperty().get()));

        ideaListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, previous, current) -> controller.select(current));

        controller.selectedIdeaProperty().addListener(
            (observable, previous, current) ->
            {
                if(current == null)
                {
                    closeDetail();
                    return;
                }

                if(ideaListView.getSelectionModel().getSelectedItem() != current)
                {
                    ideaListView.getSelectionModel().select(current);
                }

                if(detailOverlay.isVisible())
                {
                    showDetail(current);
                }
            });
    }

    /**
     * Selection alone no longer opens the panel — a click on a row or Enter does. Keeping the two
     * apart is what lets the arrow keys walk the list without a modal panel opening on every step.
     */
    private void openDetail()
    {
        Idea idea = controller.selectedIdeaProperty().get();

        if(idea == null || detailOverlay.isVisible())
        {
            return;
        }

        showDetail(idea);
        detailOverlay.setVisible(true);
        Platform.runLater(detailScroll::requestFocus);
    }

    private void closeDetail()
    {
        if(!detailOverlay.isVisible())
        {
            return;
        }

        detailOverlay.setVisible(false);
        ideaListView.requestFocus();
    }

    private void showDetail(Idea idea)
    {
        if(idea == null)
        {
            detailTitle.setText("");
            detailMeta.setText("");
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

        restoreFocus();
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

        restoreFocus();
    }

    private void deleteIdea(Idea idea)
    {
        if(idea == null)
        {
            return;
        }

        Alert confirm = new Alert(AlertType.CONFIRMATION);
        Theme.applyTo(confirm);
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

        restoreFocus();
    }

    /**
     * A dialog opened from inside the panel must hand focus back to the panel, not to the list
     * sitting behind the scrim where the focus ring would be invisible and unreachable.
     */
    private void restoreFocus()
    {
        if(detailOverlay.isVisible())
        {
            detailScroll.requestFocus();
        }
        else
        {
            ideaListView.requestFocus();
        }
    }

    private Window window()
    {
        return ideaListView.getScene() == null ? null : ideaListView.getScene().getWindow();
    }
}
