package com.emgi.timeline.view;

import com.emgi.timeline.domain.query.SortOrder;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SortMenu
{
    private static final Duration EXPAND = Duration.millis(260);
    private static final Duration PANEL_FADE_IN = Duration.millis(120);
    private static final Duration ROW_FADE_IN = Duration.millis(180);
    private static final Duration ROW_FIRST_DELAY = Duration.millis(80);
    private static final Duration ROW_STAGGER = Duration.millis(40);
    private static final Duration ROW_FADE_OUT = Duration.millis(110);
    private static final Duration COLLAPSE = Duration.millis(200);

    private static final Interpolator EASE = Interpolator.SPLINE(0.22, 1, 0.36, 1);
    private static final Interpolator EASE_IN = Interpolator.SPLINE(0.64, 0, 0.78, 0);

    private static final double ROW_RISE = 6;
    private static final double GAP = 4;
    private static final double SHADOW_PAD = 28;
    private static final double EDGE_MARGIN = 8;

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final ToggleButton trigger;
    private final Label label;
    private final SVGPath chevron;
    private final Pane layer;

    private final Pane clipHost = new Pane();
    private final VBox card = new VBox();
    private final Rectangle clipShape = new Rectangle();
    private final List<Button> rows = new ArrayList<>();

    private final DoubleProperty reveal = new SimpleDoubleProperty(0);

    private final ObjectProperty<SortOrder> value =
        new SimpleObjectProperty<>(this, "value", SortOrder.NEWEST_FIRST);

    private final EventHandler<MouseEvent> outsidePress = this::onOutsidePress;

    private Timeline animation;
    private Scene watchedScene;
    private boolean open;

    public SortMenu(ToggleButton trigger, Label label, SVGPath chevron, Pane layer)
    {
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.label = Objects.requireNonNull(label, "label");
        this.chevron = Objects.requireNonNull(chevron, "chevron");
        this.layer = Objects.requireNonNull(layer, "layer");
    }

    public ObjectProperty<SortOrder> valueProperty()
    {
        return value;
    }

    public boolean isOpen()
    {
        return open;
    }

    public void install()
    {
        buildCard();

        clipHost.setManaged(false);
        clipHost.setPickOnBounds(false);
        clipHost.setVisible(false);
        clipHost.setClip(clipShape);
        clipHost.getChildren().add(card);

        card.setManaged(false);

        layer.setVisible(false);
        layer.setPickOnBounds(false);
        layer.getChildren().add(clipHost);

        reveal.addListener((observable, previous, current) ->
        {
            double height = current.doubleValue();

            clipHost.resize(clipShape.getWidth() - SHADOW_PAD * 2, height);
            clipShape.setHeight(height + SHADOW_PAD);
        });

        trigger.setOnAction(event -> toggle());
        trigger.setOnKeyPressed(this::onTriggerKey);

        value.addListener((observable, previous, current) -> syncSelection());

        trigger.sceneProperty().addListener((observable, previous, current) -> watchScene(current));
        watchScene(trigger.getScene());

        syncSelection();
    }

    public void toggle()
    {
        if(open)
        {
            close();
            return;
        }

        show();
    }

    public void close()
    {
        if(!open)
        {
            return;
        }

        open = false;
        trigger.setSelected(false);
        card.setMouseTransparent(true);
        detachOutsidePress();

        stopAnimation();

        Timeline collapse = new Timeline();
        collapse.getKeyFrames().add(new KeyFrame(Duration.ZERO,
            new KeyValue(reveal, reveal.get()),
            new KeyValue(chevron.rotateProperty(), chevron.getRotate())));

        for(Button row : rows)
        {
            collapse.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                new KeyValue(row.opacityProperty(), row.getOpacity())));

            collapse.getKeyFrames().add(new KeyFrame(ROW_FADE_OUT,
                new KeyValue(row.opacityProperty(), 0.0, Interpolator.LINEAR)));
        }

        collapse.getKeyFrames().add(new KeyFrame(COLLAPSE,
            new KeyValue(reveal, 0.0, EASE_IN),
            new KeyValue(chevron.rotateProperty(), 0.0, EASE_IN),
            new KeyValue(clipHost.opacityProperty(), 0.0, Interpolator.LINEAR)));

        collapse.setOnFinished(event ->
        {
            clipHost.setVisible(false);
            layer.setVisible(false);
        });

        animation = collapse;
        collapse.play();
    }

    private void show()
    {
        open = true;
        trigger.setSelected(true);

        stopAnimation();
        syncSelection();
        position();

        layer.setVisible(true);
        clipHost.setVisible(true);
        clipHost.setOpacity(0);
        card.setMouseTransparent(true);

        double full = card.prefHeight(card.getWidth());

        Timeline expand = new Timeline();
        expand.getKeyFrames().add(new KeyFrame(Duration.ZERO,
            new KeyValue(reveal, 0.0),
            new KeyValue(clipHost.opacityProperty(), 0.0),
            new KeyValue(chevron.rotateProperty(), chevron.getRotate())));

        expand.getKeyFrames().add(new KeyFrame(PANEL_FADE_IN,
            new KeyValue(clipHost.opacityProperty(), 1.0, Interpolator.LINEAR)));

        expand.getKeyFrames().add(new KeyFrame(EXPAND,
            new KeyValue(reveal, full, EASE),
            new KeyValue(chevron.rotateProperty(), 180.0, EASE)));

        for(int index = 0; index < rows.size(); index++)
        {
            Button row = rows.get(index);
            Duration start = ROW_FIRST_DELAY.add(ROW_STAGGER.multiply(index));

            row.setOpacity(0);
            row.setTranslateY(-ROW_RISE);

            expand.getKeyFrames().add(new KeyFrame(start,
                new KeyValue(row.opacityProperty(), 0.0),
                new KeyValue(row.translateYProperty(), -ROW_RISE)));

            expand.getKeyFrames().add(new KeyFrame(start.add(ROW_FADE_IN),
                new KeyValue(row.opacityProperty(), 1.0, Interpolator.LINEAR),
                new KeyValue(row.translateYProperty(), 0.0, EASE)));
        }

        expand.setOnFinished(event -> card.setMouseTransparent(false));

        animation = expand;
        expand.play();

        Platform.runLater(this::attachOutsidePress);
    }

    private void buildCard()
    {
        card.getStyleClass().add("sort-menu-card");

        for(SortOrder order : SortOrder.values())
        {
            Button row = new Button(order.displayName());
            row.getStyleClass().add("sort-menu-item");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setFocusTraversable(false);
            row.setUserData(order);
            row.setOnAction(event -> choose(order));

            rows.add(row);
            card.getChildren().add(row);
        }

        card.setOnKeyPressed(this::onCardKey);
    }

    private void choose(SortOrder order)
    {
        value.set(order);
        close();
        trigger.requestFocus();
    }

    private void syncSelection()
    {
        label.setText(value.get() == null ? "" : value.get().displayName());

        for(Button row : rows)
        {
            row.pseudoClassStateChanged(SELECTED, row.getUserData() == value.get());
        }
    }

    private void position()
    {
        card.autosize();

        double width = Math.max(trigger.getWidth(), card.prefWidth(-1));
        double height = card.prefHeight(width);

        card.resizeRelocate(0, 0, width, height);
        clipShape.setX(-SHADOW_PAD);
        clipShape.setY(-SHADOW_PAD);
        clipShape.setWidth(width + SHADOW_PAD * 2);
        clipShape.setHeight(SHADOW_PAD);

        Bounds bounds = trigger.localToScene(trigger.getLayoutBounds());
        Point2D anchor = layer.sceneToLocal(bounds.getMinX(), bounds.getMaxY() + GAP);

        double x = anchor.getX();
        double overflow = x + width + EDGE_MARGIN - layer.getWidth();

        if(overflow > 0)
        {
            x = x - overflow;
        }

        clipHost.relocate(Math.max(EDGE_MARGIN, x), anchor.getY());
        reveal.set(0);
    }

    private void onTriggerKey(KeyEvent event)
    {
        if(event.getCode() == KeyCode.DOWN && !open)
        {
            toggle();
            event.consume();
            return;
        }

        if(event.getCode() == KeyCode.ESCAPE && open)
        {
            close();
            event.consume();
        }
    }

    private void onCardKey(KeyEvent event)
    {
        if(event.getCode() == KeyCode.ESCAPE)
        {
            close();
            trigger.requestFocus();
            event.consume();
        }
    }

    private void onOutsidePress(MouseEvent event)
    {
        if(!open)
        {
            return;
        }

        if(event.getTarget() instanceof Node target && isInside(target))
        {
            return;
        }

        close();
    }

    private boolean isInside(Node target)
    {
        for(Node node = target; node != null; node = node.getParent())
        {
            if(node == card || node == trigger)
            {
                return true;
            }
        }

        return false;
    }

    private void watchScene(Scene scene)
    {
        if(watchedScene == scene)
        {
            return;
        }

        detachOutsidePress();
        watchedScene = scene;

        if(scene == null)
        {
            return;
        }

        scene.widthProperty().addListener((observable, previous, current) -> close());
        scene.heightProperty().addListener((observable, previous, current) -> close());

        if(open)
        {
            attachOutsidePress();
        }
    }

    private void attachOutsidePress()
    {
        if(watchedScene != null)
        {
            watchedScene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsidePress);
        }
    }

    private void detachOutsidePress()
    {
        if(watchedScene != null)
        {
            watchedScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsidePress);
        }
    }

    private void stopAnimation()
    {
        if(animation != null)
        {
            animation.stop();
            animation = null;
        }
    }
}