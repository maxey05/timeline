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
import javafx.scene.shape.SVGPath;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MainView
{
    private static final ButtonType DELETE =
            new ButtonType("Delete", ButtonBar.ButtonData.OK_DONE);

    private static final double PANEL_WIDTH_FRACTION = 0.72;
    private static final double PANEL_HEIGHT_FRACTION = 0.80;
    private static final double PANEL_MAX_WIDTH = 720;
    private static final double PANEL_MAX_HEIGHT = 620;

    private static final double BACKDROP_BLUR = 10;

    private static final Duration OVERLAY_FADE = Duration.millis(140);

    private static final double NAME_PANEL_WIDTH = 400;

    private final IdeaListController controller;
    private final IdeaDateFormatter dateFormatter;
    private final IdeaEditorOverlay editors;
    private final DescriptionRenderer descriptionRenderer;
    private final DisplayNameStore displayNames;

    private SortMenu sortMenu;

    @FXML
    private Label appTitle;

    @FXML
    private HBox titleBar;

    @FXML
    private Button minimizeButton;

    @FXML
    private Button maximizeButton;

    @FXML
    private SVGPath maximizeGlyph;

    @FXML
    private Button closeButton;

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
    private ToggleButton sortToggle;

    @FXML
    private Label sortLabel;

    @FXML
    private SVGPath sortChevron;

    @FXML
    private Pane sortMenuLayer;

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

    private final StringProperty displayName = new SimpleStringProperty("");
    private final ContextMenu settingsMenu = new ContextMenu();

    private IdeaEditorOverlay.Session editorSession;

    private boolean detailOpen;
    private boolean editorOpen;
    private boolean namePromptOpen;

    private boolean editorClosing;

    private final FadeTransition detailFade = new FadeTransition(OVERLAY_FADE);
    private final FadeTransition editorFade = new FadeTransition(OVERLAY_FADE);
    private final FadeTransition namePromptFade = new FadeTransition(OVERLAY_FADE);

    private final GaussianBlur contentBlur = new GaussianBlur(BACKDROP_BLUR);
    private final GaussianBlur detailBlur = new GaussianBlur(BACKDROP_BLUR);

    private WindowChrome windowChrome;

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
        if(appTitle == null || titleBar == null || minimizeButton == null
            || maximizeButton == null || maximizeGlyph == null || closeButton == null
            || contentRoot == null || ideaListView == null || emptyState == null || newIdeaButton == null
            || settingsButton == null
            || searchField == null || sortToggle == null || sortLabel == null
            || sortChevron == null || sortMenuLayer == null || tagFilterRow == null
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

        buildTitleBar();
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

                windowChrome.install(current);

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

    private void onSceneKey(KeyEvent event)
    {
        if(editorClosing)
        {
            event.consume();
            return;
        }

        if(sortMenu != null && sortMenu.isOpen())
        {
            if(event.getCode() == KeyCode.ESCAPE)
            {
                sortMenu.close();
                event.consume();
            }

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

    private void buildTitleBar()
    {
        closeButton.getStyleClass().add("caption-close");

        windowChrome = new WindowChrome(
            titleBar, minimizeButton, maximizeButton, maximizeGlyph, closeButton);
    }

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

    public StringProperty displayNameProperty()
    {
        return displayName;
    }

    private void buildSettingsMenu()
    {
        settingsButton.setAccessibleText("Settings");
        settingsButton.setTooltip(new Tooltip("Settings"));

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

        sortMenu = new SortMenu(sortToggle, sortLabel, sortChevron, sortMenuLayer);
        sortMenu.install();
        sortMenu.valueProperty().bindBidirectional(controller.sortOrderProperty());

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

        detailPanel.maxWidthProperty().bind(Bindings.min(
            contentRoot.widthProperty().multiply(PANEL_WIDTH_FRACTION), PANEL_MAX_WIDTH));
        detailPanel.maxHeightProperty().bind(Bindings.min(
            contentRoot.heightProperty().multiply(PANEL_HEIGHT_FRACTION), PANEL_MAX_HEIGHT));

        overlayFocusRing = List.of(detailScroll, detailEditButton, detailCloseButton);

        detailScrim.setOnMouseClicked(event ->
        {
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

        editorHost.maxWidthProperty().bind(Bindings.min(
            contentRoot.widthProperty().multiply(PANEL_WIDTH_FRACTION), PANEL_MAX_WIDTH));
        editorHost.maxHeightProperty().bind(Bindings.min(
            contentRoot.heightProperty().multiply(PANEL_HEIGHT_FRACTION), PANEL_MAX_HEIGHT));

        editorScrim.setOnMouseClicked(MouseEvent::consume);
    }

    private void buildNamePrompt()
    {
        namePromptOverlay.setVisible(false);
        namePromptOverlay.setOpacity(0);
        namePromptOverlay.managedProperty().bind(namePromptOverlay.visibleProperty());
        namePromptFade.setNode(namePromptOverlay);

        namePromptPanel.setMaxWidth(NAME_PANEL_WIDTH);

        namePromptPanel.setMaxHeight(Region.USE_PREF_SIZE);

        nameOkButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> DisplayNameStore.normalize(nameField.getText()).isEmpty(),
            nameField.textProperty()));

        nameField.setOnAction(event -> submitName());
        nameOkButton.setOnAction(event -> submitName());

        namePromptFocusRing = List.of(nameField, nameOkButton);

        namePromptScrim.setOnMouseClicked(MouseEvent::consume);
    }

    private void openNamePromptIfUnnamed()
    {
        sortMenu.close();

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

    private void syncBackdrop()
    {
        contentRoot.setEffect(editorOpen || detailOpen || namePromptOpen ? contentBlur : null);

        detailOverlay.setEffect(editorOpen ? detailBlur : null);
    }

    private void openEditor(IdeaEditorOverlay.Session session)
    {
        sortMenu.close();

        editorSession = session;
        editorOpen = true;

        editorHost.getChildren().setAll(session.root());
        fadeIn(editorOverlay, editorFade);
        syncBackdrop();

        session.view().attach(window(), ideaListView.getScene(), () -> closeEditor(session));
    }

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

    private void openDetail()
    {
        sortMenu.close();

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
