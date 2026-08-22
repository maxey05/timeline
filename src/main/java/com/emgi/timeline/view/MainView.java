package com.emgi.timeline.view;

import com.emgi.timeline.controller.IdeaListController;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.domain.query.SortOrder;
import com.emgi.timeline.settings.DisplayNameStore;
import com.emgi.timeline.view.cell.IdeaListCell;
import com.emgi.timeline.view.content.DescriptionRenderer;
import com.emgi.timeline.view.format.IdeaDateFormatter;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import javafx.scene.effect.GaussianBlur;
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
import javafx.util.Duration;
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

    /*
     * JavaFX's blur radius is the full kernel extent, not the standard deviation, so it runs
     * about three times the number a CSS blur() would take for the same softness: 10 here is
     * a sigma of roughly 3.3. It was 18, which turned the list behind the panel into soup and
     * cost the backdrop the one thing it is there to say -- your place in the list is still
     * where you left it.
     */
    private static final double BACKDROP_BLUR = 10;

    /**
     * How long an overlay takes to fade in or out. Long enough to read as a movement rather
     * than a screen change, short enough that opening ten ideas in a row is never waited on.
     */
    private static final Duration OVERLAY_FADE = Duration.millis(140);

    /**
     * The name prompt does not scale with the window like the other two panels. It holds one
     * short field, and a field that grew to 720px would look like a mistake rather than a
     * question.
     */
    private static final double NAME_PANEL_WIDTH = 400;

    private final IdeaListController controller;
    private final IdeaDateFormatter dateFormatter;
    private final IdeaEditorOverlay editors;
    private final DescriptionRenderer descriptionRenderer;
    private final DisplayNameStore displayNames;

    @FXML
    private Label appTitle;

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
    private VBox descriptionWell;

    @FXML
    private Button detailCloseButton;

    @FXML
    private Button detailEditButton;

    @FXML
    private StackPane editorOverlay;

    @FXML
    private Region editorScrim;

    @FXML
    private StackPane editorHost;

    @FXML
    private StackPane namePromptOverlay;

    @FXML
    private Region namePromptScrim;

    @FXML
    private VBox namePromptPanel;

    @FXML
    private TextField nameField;

    @FXML
    private Button nameOkButton;

    private final ToggleButton allTagsChip = new ToggleButton("All");

    /** The name the header greets by. Empty until the user has given one. */
    private final StringProperty displayName = new SimpleStringProperty("");
    private final ContextMenu settingsMenu = new ContextMenu();

    /** The editor currently on screen, or null. Its result is read when it closes. */
    private IdeaEditorOverlay.Session editorSession;

    /**
     * Whether each overlay is logically open, which is not the same as whether its node is
     * visible -- an overlay stays visible while it fades out. Everything that asks "is the
     * panel up?" (keyboard routing, the backdrop, where focus goes) asks these instead, so a
     * panel stops taking part the moment the user closes it rather than a fade later.
     */
    private boolean detailOpen;
    private boolean editorOpen;
    private boolean namePromptOpen;

    /**
     * True from the moment the editor is closed until its fade ends and it is torn down. The
     * editor's own key filter is still live on the shared scene for that stretch, which is
     * what makes a second Ctrl+Enter inside the fade able to save the same idea twice.
     */
    private boolean editorClosing;

    private final FadeTransition detailFade = new FadeTransition(OVERLAY_FADE);
    private final FadeTransition editorFade = new FadeTransition(OVERLAY_FADE);
    private final FadeTransition namePromptFade = new FadeTransition(OVERLAY_FADE);

    /*
     * Two instances, not one shared between the nodes: an Effect belongs to the node it is
     * set on, and handing the same object to two of them is asking for a rendering bug.
     */
    private final GaussianBlur contentBlur = new GaussianBlur(BACKDROP_BLUR);
    private final GaussianBlur detailBlur = new GaussianBlur(BACKDROP_BLUR);

    /** Tab cycles within these while the panel is open, so focus never escapes behind the scrim. */
    private List<Node> overlayFocusRing = List.of();
    private List<Node> namePromptFocusRing = List.of();

    public MainView(IdeaListController controller,
                    IdeaDateFormatter dateFormatter,
                    IdeaEditorOverlay editors,
                    DescriptionRenderer descriptionRenderer,
                    DisplayNameStore displayNames)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.dateFormatter = Objects.requireNonNull(dateFormatter, "dateFormatter");
        this.editors = Objects.requireNonNull(editors, "editors");
        this.descriptionRenderer =
            Objects.requireNonNull(descriptionRenderer, "descriptionRenderer");
        this.displayNames = Objects.requireNonNull(displayNames, "displayNames");
    }

    @FXML
    private void initialize()
    {
        if(appTitle == null || contentRoot == null || ideaListView == null || emptyState == null || newIdeaButton == null
            || settingsButton == null
            || searchField == null || sortChoice == null || tagFilterRow == null
            || tagFilterPane == null || noMatchesState == null || clearFiltersButton == null
            || detailOverlay == null || detailScrim == null || detailPanel == null
            || detailTitle == null || detailMeta == null || detailTags == null
            || detailScroll == null || descriptionWell == null || detailCloseButton == null
            || detailEditButton == null
            || editorOverlay == null || editorScrim == null || editorHost == null
            || namePromptOverlay == null || namePromptScrim == null || namePromptPanel == null
            || nameField == null || nameOkButton == null)
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

        buildHeader();
        buildSettingsMenu();
        buildFilterControls();
        buildDetailOverlay();
        buildEditorOverlay();
        buildNamePrompt();
        buildBackdrop();
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

                // Queued after installAccelerators' own runLater, so the field wins the focus
                // the list would otherwise have taken.
                Platform.runLater(this::openNamePromptIfUnnamed);
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
        if(editorClosing)
        {
            /*
             * The editor is still on the scene while it fades, with its own key filter still
             * live on it, so an unguarded Ctrl+Enter inside that window would run save() a
             * second time and write a second copy of the idea. Nothing gets keys until the
             * editor is gone.
             */
            event.consume();
            return;
        }

        if(namePromptOpen)
        {
            onNamePromptKey(event);
            return;
        }

        if(editorOpen)
        {
            onEditorKey(event);
            return;
        }

        if(!detailOpen)
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

        if(event.isShortcutDown() || !isInside(event.getTarget(), detailOverlay))
        {
            event.consume();
        }
    }

    /**
     * While the prompt is up it is the only thing on screen. Esc leaves without a name -- that
     * is not an error, the question simply comes back next launch -- Tab stays inside, and
     * nothing else, accelerators included, gets a key.
     */
    private void onNamePromptKey(KeyEvent event)
    {
        if(event.getCode() == KeyCode.ESCAPE)
        {
            closeNamePrompt();
            event.consume();
            return;
        }

        if(event.getCode() == KeyCode.TAB)
        {
            cycleFocus(namePromptFocusRing, event.isShiftDown());
            event.consume();
            return;
        }

        if(event.isShortcutDown() || !isInside(event.getTarget(), namePromptOverlay))
        {
            event.consume();
        }
    }

    /**
     * Far less greedy than the detail panel's branch. IdeaEditorView installs its own filter
     * on this same scene for Ctrl+Enter, Ctrl+I and Ctrl+V, and this filter was registered
     * first, so it runs first -- consuming shortcuts wholesale here would eat the editor's
     * own before they ever reached it. Only the two accelerators MainView owns are muted.
     */
    private void onEditorKey(KeyEvent event)
    {
        if(editorSession == null)
        {
            return;
        }

        if(event.getCode() == KeyCode.ESCAPE)
        {
            editorSession.view().requestClose();
            event.consume();
            return;
        }

        if(event.getCode() == KeyCode.TAB)
        {
            cycleFocus(editorSession.view().focusRing(), event.isShiftDown());
            event.consume();
            return;
        }

        if(event.isShortcutDown()
            && (event.getCode() == KeyCode.N || event.getCode() == KeyCode.F))
        {
            event.consume();
        }
    }

    private void cycleOverlayFocus(boolean backwards)
    {
        cycleFocus(overlayFocusRing, backwards);
    }

    private void cycleFocus(List<Node> ring, boolean backwards)
    {
        if(ring.isEmpty())
        {
            return;
        }

        Scene scene = ideaListView.getScene();
        Node focused = scene == null ? null : scene.getFocusOwner();

        // List.of(...).indexOf(null) throws — an immutable list rejects a null probe.
        int current = focused == null ? -1 : ring.indexOf(focused);
        int size = ring.size();
        int next = current < 0 ? 0 : ((current + (backwards ? -1 : 1)) + size) % size;

        ring.get(next).requestFocus();
    }

    private static boolean isInside(EventTarget target, Node ancestor)
    {
        if(!(target instanceof Node node))
        {
            return false;
        }

        for(Node current = node; current != null; current = current.getParent())
        {
            if(current == ancestor)
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
     * The header greets the user by name once there is a name to greet them by, and says the
     * application's own name until then. First launch asks for one; App sets the property from
     * what is stored, and again from what the prompt comes back with.
     */
    private void buildHeader()
    {
        displayNames.load().ifPresent(displayName::set);

        appTitle.textProperty().bind(
            Bindings.createStringBinding(() -> greeting(displayName.get()), displayName));
    }

    private static String greeting(String name)
    {
        return name == null || name.isBlank()
            ? "Welcome to your timeline."
            : "Welcome to your timeline, " + name + ".";
    }

    /** The name in the header. Writable so that Options can change it without a restart. */
    public StringProperty displayNameProperty()
    {
        return displayName;
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
        detailOverlay.setOpacity(0);
        detailOverlay.managedProperty().bind(detailOverlay.visibleProperty());
        detailFade.setNode(detailOverlay);

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

                if(detailOpen)
                {
                    showDetail(current);
                }
            });
    }

    private void buildEditorOverlay()
    {
        editorOverlay.setVisible(false);
        editorOverlay.setOpacity(0);
        editorOverlay.managedProperty().bind(editorOverlay.visibleProperty());
        editorFade.setNode(editorOverlay);

        // The same fractions and ceilings the detail panel uses, so the two modals are the
        // same size object at every window size.
        editorHost.maxWidthProperty().bind(Bindings.min(
            contentRoot.widthProperty().multiply(PANEL_WIDTH_FRACTION), PANEL_MAX_WIDTH));
        editorHost.maxHeightProperty().bind(Bindings.min(
            contentRoot.heightProperty().multiply(PANEL_HEIGHT_FRACTION), PANEL_MAX_HEIGHT));

        /*
         * The scrim swallows the click and does nothing else. The detail panel closes on a
         * scrim click because there is nothing to lose there; here a stray click beside a
         * half-written idea would either throw it away or throw a confirm dialog over the
         * user's own typing. Esc and Cancel are the ways out.
         */
        editorScrim.setOnMouseClicked(MouseEvent::consume);
    }

    private void buildNamePrompt()
    {
        namePromptOverlay.setVisible(false);
        namePromptOverlay.setOpacity(0);
        namePromptOverlay.managedProperty().bind(namePromptOverlay.visibleProperty());
        namePromptFade.setNode(namePromptOverlay);

        namePromptPanel.setMaxWidth(NAME_PANEL_WIDTH);

        /*
         * Without this the panel stretches from the top of the window to the bottom: a Region
         * whose maxHeight is USE_COMPUTED_SIZE resolves it to Double.MAX_VALUE, not to the
         * height it would prefer.
         */
        namePromptPanel.setMaxHeight(Region.USE_PREF_SIZE);

        /*
         * OK is the only way out that leaves a name behind, so it stays disabled until there
         * is one to leave -- which is also what a panel with no Cancel button owes the user:
         * something on screen saying what it is waiting for.
         */
        nameOkButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> DisplayNameStore.normalize(nameField.getText()).isEmpty(),
            nameField.textProperty()));

        nameField.setOnAction(event -> submitName());
        nameOkButton.setOnAction(event -> submitName());

        namePromptFocusRing = List.of(nameField, nameOkButton);

        // A stray click does not dismiss the one question the application ever asks.
        namePromptScrim.setOnMouseClicked(MouseEvent::consume);
    }

    /** Asks for a name, once, on the first launch that finds none stored. */
    private void openNamePromptIfUnnamed()
    {
        if(namePromptOpen || displayNames.load().isPresent())
        {
            return;
        }

        namePromptOpen = true;
        nameField.clear();
        fadeIn(namePromptOverlay, namePromptFade);
        syncBackdrop();
        nameField.requestFocus();
    }

    /**
     * OK, or Enter in the field.
     *
     * <p>The name is stored and the header rewritten <em>before</em> the fade starts, so the
     * greeting the user watches come back into focus behind the panel is already their own.</p>
     */
    private void submitName()
    {
        Optional<String> name = DisplayNameStore.normalize(nameField.getText());

        if(name.isEmpty())
        {
            return;
        }

        displayNames.save(name.get());
        displayName.set(name.get());
        closeNamePrompt();
    }

    private void closeNamePrompt()
    {
        if(!namePromptOpen)
        {
            return;
        }

        namePromptOpen = false;
        fadeOut(namePromptOverlay, namePromptFade, this::syncBackdrop);
        ideaListView.requestFocus();
    }

    /**
     * Ties the blur strength to the overlays' own opacity, so the window behind sharpens at
     * exactly the rate the panel over it fades. Without this the blur would snap off at the
     * end of a fade-out, and a snap is the one thing that makes a transition read as a bug.
     */
    private void buildBackdrop()
    {
        contentBlur.radiusProperty().bind(
            Bindings.max(
                Bindings.max(detailOverlay.opacityProperty(), editorOverlay.opacityProperty()),
                namePromptOverlay.opacityProperty())
                    .multiply(BACKDROP_BLUR));

        detailBlur.radiusProperty().bind(
            editorOverlay.opacityProperty().multiply(BACKDROP_BLUR));
    }

    /**
     * Brings an overlay up. Interrupting a fade that is still running is normal -- reopening
     * the panel a user just closed is one gesture -- so the fade starts from wherever the
     * opacity currently is, and any teardown the interrupted fade was going to run is dropped.
     */
    private static void fadeIn(Node overlay, FadeTransition fade)
    {
        fade.stop();
        fade.setOnFinished(null);

        overlay.setMouseTransparent(false);
        overlay.setVisible(true);
        fade.setFromValue(overlay.getOpacity());
        fade.setToValue(1);
        fade.play();
    }

    /**
     * Takes an overlay down, and runs {@code afterwards} once it is actually gone.
     *
     * <p>It stays visible for the length of the fade, which is the point, but it is made
     * mouse-transparent for that stretch: the scrim is still lying over the whole window and
     * would otherwise swallow a click aimed at the list showing through it.</p>
     */
    private static void fadeOut(Node overlay, FadeTransition fade, Runnable afterwards)
    {
        fade.stop();

        overlay.setMouseTransparent(true);
        fade.setFromValue(overlay.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(event ->
        {
            overlay.setVisible(false);
            afterwards.run();
        });
        fade.play();
    }

    /**
     * The backdrop behind whichever overlay is on top. The effect goes on the nodes BEHIND
     * the overlay, never on the overlay itself -- they are siblings under the root StackPane,
     * which is the only reason this is one line each rather than a snapshot dance.
     *
     * <p>Called as an overlay opens, and again only once its fade has finished, so the effect
     * outlives the panel it belongs to by exactly the length of the fade.</p>
     */
    private void syncBackdrop()
    {
        contentRoot.setEffect(editorOpen || detailOpen || namePromptOpen ? contentBlur : null);

        // The editor can open over an open detail panel, via that panel's Edit... button, so
        // the panel is backdrop too. Its own scrim blurs to itself and stacks with the
        // editor's, which is why neither is drawn at full strength.
        detailOverlay.setEffect(editorOpen ? detailBlur : null);
    }

    private void openEditor(IdeaEditorOverlay.Session session)
    {
        editorSession = session;
        editorOpen = true;

        editorHost.getChildren().setAll(session.root());
        fadeIn(editorOverlay, editorFade);
        syncBackdrop();

        session.view().attach(window(), ideaListView.getScene(), () -> closeEditor(session));
    }

    /**
     * What used to be the line after showAndWait(). The editor cannot block, so the result is
     * read here instead, from the callback the view was handed when it opened.
     */
    private void closeEditor(IdeaEditorOverlay.Session session)
    {
        if(editorSession != session)
        {
            return;
        }

        editorOpen = false;
        editorClosing = true;
        editorSession = null;

        fadeOut(editorOverlay, editorFade, () -> finishEditorClose(session));
    }

    /**
     * The other half of closing, run once the editor has finished fading out. Both lines at
     * the top of it have to wait for that: detaching disposes the description area, which
     * empties it on screen, and clearing the host would leave an empty box fading out where
     * the user could still see an editor.
     */
    private void finishEditorClose(IdeaEditorOverlay.Session session)
    {
        session.view().detach();

        editorHost.getChildren().clear();
        editorClosing = false;
        syncBackdrop();

        IdeaEditorOverlay.Result result = session.result();

        if(session.creating())
        {
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
        else
        {
            result.saved().ifPresent(controller::replace);

            if(result.targetMissing())
            {
                controller.load();
            }
        }

        restoreFocus();
    }

    /**
     * Selection alone no longer opens the panel — a click on a row or Enter does. Keeping the two
     * apart is what lets the arrow keys walk the list without a modal panel opening on every step.
     */
    private void openDetail()
    {
        Idea idea = controller.selectedIdeaProperty().get();

        if(idea == null || detailOpen)
        {
            return;
        }

        showDetail(idea);
        detailOpen = true;
        fadeIn(detailOverlay, detailFade);
        syncBackdrop();
        Platform.runLater(detailScroll::requestFocus);
    }

    private void closeDetail()
    {
        if(!detailOpen)
        {
            return;
        }

        detailOpen = false;
        fadeOut(detailOverlay, detailFade, this::syncBackdrop);
        ideaListView.requestFocus();
    }

    private void showDetail(Idea idea)
    {
        if(idea == null)
        {
            detailTitle.setText("");
            detailMeta.setText("");
            descriptionWell.getChildren().clear();
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

        descriptionWell.getChildren().setAll(descriptionRenderer.renderAll(idea.description()));
        detailScroll.setVvalue(0);
    }

    private void createIdea()
    {
        if(editorOpen || editorClosing)
        {
            return;
        }

        openEditor(editors.createSession());
    }

    private void editIdea(Idea idea)
    {
        if(idea == null || editorOpen || editorClosing)
        {
            return;
        }

        openEditor(editors.editSession(idea));
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
        if(detailOpen)
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
