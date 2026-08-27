package com.emgi.timeline.view;

import com.emgi.timeline.settings.AppSettings;
import com.emgi.timeline.settings.SettingsController;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class SettingsPanel
{
    public static final double PANEL_WIDTH = 480;

    private static final Duration FADE = Duration.millis(140);
    private static final Duration NO_FADE = Duration.millis(1);

    private static final String DISCARD_TITLE = "Discard changes?";
    private static final String DISCARD_MESSAGE =
        "You have changed settings that have not been saved yet.";
    private static final String DISCARD_CONFIRM = "Discard";
    private static final String DISCARD_CANCEL = "Cancel";

    private static final String NAME_PROMPT_FALLBACK = "enter your name…";

    private final SettingsController controller;
    private final ConfirmPrompt confirms;

    private final StackPane overlay;
    private final Region scrim;
    private final VBox panel;

    private final HBox nameRow;
    private final HBox themeRow;
    private final HBox animationsRow;

    private final TextField nameField;
    private final ToggleSwitch themeSwitch;
    private final ToggleSwitch animationsSwitch;

    private final Button saveButton;
    private final Button closeButton;

    private final FadeTransition fade = new FadeTransition(FADE);

    private List<Node> focusRing = List.of();

    private Consumer<AppSettings> onSaved = settings -> { };

    private Runnable onVisibilityChanged = () -> { };

    private boolean open;

    private Node returnFocus;

    public SettingsPanel(SettingsController controller,
                         ConfirmPrompt confirms,
                         StackPane overlay,
                         Region scrim,
                         VBox panel,
                         HBox nameRow,
                         HBox themeRow,
                         HBox animationsRow,
                         TextField nameField,
                         ToggleSwitch themeSwitch,
                         ToggleSwitch animationsSwitch,
                         Button saveButton,
                         Button closeButton)
    {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.confirms = Objects.requireNonNull(confirms, "confirms");
        this.overlay = Objects.requireNonNull(overlay, "overlay");
        this.scrim = Objects.requireNonNull(scrim, "scrim");
        this.panel = Objects.requireNonNull(panel, "panel");
        this.nameRow = Objects.requireNonNull(nameRow, "nameRow");
        this.themeRow = Objects.requireNonNull(themeRow, "themeRow");
        this.animationsRow = Objects.requireNonNull(animationsRow, "animationsRow");
        this.nameField = Objects.requireNonNull(nameField, "nameField");
        this.themeSwitch = Objects.requireNonNull(themeSwitch, "themeSwitch");
        this.animationsSwitch = Objects.requireNonNull(animationsSwitch, "animationsSwitch");
        this.saveButton = Objects.requireNonNull(saveButton, "saveButton");
        this.closeButton = Objects.requireNonNull(closeButton, "closeButton");
    }

    public void install()
    {
        overlay.setVisible(false);
        overlay.setOpacity(0);
        overlay.managedProperty().bind(overlay.visibleProperty());
        fade.setNode(overlay);

        panel.setMaxWidth(PANEL_WIDTH);
        panel.setMaxHeight(Region.USE_PREF_SIZE);

        nameField.textProperty().bindBidirectional(controller.displayNameInputProperty());

        themeSwitch.selectedProperty().bindBidirectional(controller.darkThemeProperty());
        animationsSwitch.selectedProperty().bindBidirectional(controller.animationsEnabledProperty());

        saveButton.disableProperty().bind(controller.dirtyBinding().not());

        saveButton.setOnAction(event -> save());
        closeButton.setOnAction(event -> requestClose());

        installRowToggle(themeRow, themeSwitch);
        installRowToggle(animationsRow, animationsSwitch);

        nameRow.setOnMouseClicked(event ->
        {
            if(!isInside(event.getTarget(), nameField))
            {
                nameField.requestFocus();
            }
        });

        scrim.setOnMouseClicked(event ->
        {
            requestClose();
            event.consume();
        });

        focusRing = List.of(nameField, themeSwitch, animationsSwitch, saveButton, closeButton);
    }

    private void installRowToggle(HBox row, ToggleSwitch control)
    {
        row.setOnMouseClicked(event ->
        {
            if(isInside(event.getTarget(), control))
            {
                return;
            }

            control.requestFocus();
            control.toggle();
            event.consume();
        });
    }

    public void open()
    {
        if(open)
        {
            return;
        }

        Scene scene = overlay.getScene();

        open = true;
        returnFocus = scene == null ? null : scene.getFocusOwner();

        controller.reload();

        boolean animate = controller.saved().animationsEnabled();

        themeSwitch.setAnimated(animate);
        animationsSwitch.setAnimated(animate);

        themeSwitch.settle();
        animationsSwitch.settle();

        nameField.setPromptText(controller.saved().name().orElse(NAME_PROMPT_FALLBACK));

        fade.setDuration(animate ? FADE : NO_FADE);
        fade.stop();
        fade.setOnFinished(null);

        overlay.setMouseTransparent(false);
        overlay.setVisible(true);
        fade.setFromValue(overlay.getOpacity());
        fade.setToValue(1);
        fade.play();

        onVisibilityChanged.run();

        Platform.runLater(nameField::requestFocus);
    }

    public void requestClose()
    {
        if(!open)
        {
            return;
        }

        if(!controller.isDirty())
        {
            close();
            return;
        }

        confirms.ask(DISCARD_TITLE, DISCARD_MESSAGE, DISCARD_CONFIRM, DISCARD_CANCEL, confirmed ->
        {
            if(confirmed)
            {
                controller.revert();
                close();
            }
        });
    }

    private void save()
    {
        if(!open || !controller.isDirty())
        {
            return;
        }

        controller.save();
        onSaved.accept(controller.saved());
        close();
    }

    private void close()
    {
        if(!open)
        {
            return;
        }

        open = false;

        Node focus = returnFocus;
        returnFocus = null;

        fade.setDuration(controller.saved().animationsEnabled() ? FADE : NO_FADE);
        fade.stop();

        overlay.setMouseTransparent(true);
        fade.setFromValue(overlay.getOpacity());
        fade.setToValue(0);
        fade.setOnFinished(event ->
        {
            overlay.setVisible(false);
            onVisibilityChanged.run();
        });
        fade.play();

        onVisibilityChanged.run();

        if(focus != null)
        {
            focus.requestFocus();
        }
    }

    public void onKey(KeyEvent event)
    {
        if(event.getCode() == KeyCode.ESCAPE)
        {
            requestClose();
            event.consume();
            return;
        }

        if(event.getCode() == KeyCode.ENTER && !saveButton.isDisabled())
        {
            save();
            event.consume();
            return;
        }

        if(event.getCode() == KeyCode.TAB)
        {
            cycleFocus(event.isShiftDown());
            event.consume();
            return;
        }

        if(event.isShortcutDown() || !isInside(event.getTarget(), overlay))
        {
            event.consume();
        }
    }

    private void cycleFocus(boolean backwards)
    {
        Scene scene = overlay.getScene();
        Node focused = scene == null ? null : scene.getFocusOwner();

        int current = focused == null ? -1 : focusRing.indexOf(focused);
        int size = focusRing.size();
        int next = current < 0 ? 0 : ((current + (backwards ? -1 : 1)) + size) % size;

        focusRing.get(next).requestFocus();
    }

    private static boolean isInside(Object target, Node ancestor)
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

    public boolean isOpen()
    {
        return open;
    }

    public StackPane overlay()
    {
        return overlay;
    }

    public void setOnSaved(Consumer<AppSettings> handler)
    {
        this.onSaved = handler == null ? settings -> { } : handler;
    }

    public void setOnVisibilityChanged(Runnable handler)
    {
        this.onVisibilityChanged = handler == null ? () -> { } : handler;
    }
}