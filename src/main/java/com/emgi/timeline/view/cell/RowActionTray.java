package com.emgi.timeline.view.cell;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
 
import java.util.Objects;

public final class RowActionTray extends Pane
{
    public static final double TRAY_WIDTH = 150;
    public static final double ITEM_WIDTH = 75;
 
    private static final String EDIT_PATH =
        "M 17 3 a 2.828 2.828 0 1 1 4 4 L 7.5 20.5 L 2 22 l 1.5 -5.5 L 17 3 z";
 
    private static final String DELETE_PATH =
        "M 3 6 h 18 M 19 6 v 14 a 2 2 0 0 1 -2 2 H 7 a 2 2 0 0 1 -2 -2 V 6 "
        + "m 3 0 V 4 a 2 2 0 0 1 2 -2 h 4 a 2 2 0 0 1 2 2 v 2 M 10 11 v 6 M 14 11 v 6";
 
    private static final double GLYPH_SCALE = 0.6667;
 
    private static final Duration OPEN = Duration.millis(260);
    private static final Duration ITEM_FADE_IN = Duration.millis(180);
    private static final Duration ITEM_FIRST_DELAY = Duration.millis(80);
    private static final Duration ITEM_STAGGER = Duration.millis(40);
    private static final Duration ITEM_FADE_OUT = Duration.millis(110);
    private static final Duration CLOSE = Duration.millis(200);
 
    private static final Interpolator EASE_OUT = Interpolator.SPLINE(0.22, 1, 0.36, 1);
    private static final Interpolator EASE_IN = Interpolator.SPLINE(0.64, 0, 0.78, 0);
 
    private final Region content;
    private final HBox tray = new HBox();
    private final Button editButton = new Button("Edit");
    private final Button deleteButton = new Button("Delete");
    private final Rectangle clip = new Rectangle();
    private final DoubleProperty shift = new SimpleDoubleProperty(this, "shift", 0);
 
    private Timeline animation;
    private boolean open;

    public RowActionTray(Region content, Runnable onEdit, Runnable onDelete) 
    { 
        this.content = Objects.requireNonNull(content, "content");
        Objects.requireNonNull(onEdit, "onEdit");
        Objects.requireNonNull(onDelete, "onDelete");
 
        tray.getStyleClass().add("row-action-tray");
        tray.setAlignment(Pos.CENTER);
        tray.setManaged(false);
        tray.setMouseTransparent(true);
 
        configure(editButton, EDIT_PATH, onEdit);
        configure(deleteButton, DELETE_PATH, onDelete);
        deleteButton.getStyleClass().add("row-action-delete");
 
        tray.getChildren().addAll(editButton, deleteButton);
 
        content.translateXProperty().bind(shift);
        tray.translateXProperty().bind(shift);
 
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
 
        getChildren().addAll(content, tray);
    }

    private void configure(Button button, String path, Runnable action)
    {
        SVGPath glyph = new SVGPath();
        glyph.setContent(path);
        glyph.getStyleClass().add("row-action-glyph");
        glyph.setScaleX(GLYPH_SCALE);
        glyph.setScaleY(GLYPH_SCALE);
 
        StackPane box = new StackPane(glyph);
        box.getStyleClass().add("row-action-box");
 
        button.getStyleClass().add("row-action-item");
        button.setGraphic(box);
        button.setContentDisplay(ContentDisplay.TOP);
        button.setFocusTraversable(true);
        button.setMinWidth(ITEM_WIDTH);
        button.setPrefWidth(ITEM_WIDTH);
        button.setMaxWidth(ITEM_WIDTH);
        button.setMaxHeight(Double.MAX_VALUE);
        button.setOpacity(0);
        button.setOnAction(event ->
        {
            action.run();
            event.consume();
        });
    }

    public Region tray()
    {
        return tray;
    }

    public boolean isOpen()
    {
        return open;
    }

    public void open(boolean animated) 
    { 
        if(open && animation == null)
        {
            return;
        }

        open = true;
        stopAnimation();
 
        if(!animated)
        {
            snapOpen();
            return;
        }
 
        tray.setMouseTransparent(true);
 
        Timeline expand = new Timeline();
 
        expand.getKeyFrames().add(new KeyFrame(Duration.ZERO,
            new KeyValue(shift, shift.get()),
            new KeyValue(editButton.opacityProperty(), editButton.getOpacity()),
            new KeyValue(deleteButton.opacityProperty(), deleteButton.getOpacity())));
 
        expand.getKeyFrames().add(new KeyFrame(OPEN,
            new KeyValue(shift, -TRAY_WIDTH, EASE_OUT)));
 
        fadeIn(expand, editButton, ITEM_FIRST_DELAY);
        fadeIn(expand, deleteButton, ITEM_FIRST_DELAY.add(ITEM_STAGGER));
 
        expand.setOnFinished(event ->
        {
            animation = null;
            tray.setMouseTransparent(false);
        });
 
        animation = expand;
        expand.play();
    }

    private void fadeIn(Timeline timeline, Button button, Duration start)
    {
        timeline.getKeyFrames().add(new KeyFrame(start,
            new KeyValue(button.opacityProperty(), button.getOpacity())));

        timeline.getKeyFrames().add(new KeyFrame(start.add(ITEM_FADE_IN),
            new KeyValue(button.opacityProperty(), 1.0, Interpolator.LINEAR)));
    }

    public void close(boolean animated) 
    { 
        if(!open && animation == null)
        {
            return;
        }
 
        open = false;
        stopAnimation();
        tray.setMouseTransparent(true);
 
        if(!animated)
        {
            snapClosed();
            return;
        }
 
        Timeline collapse = new Timeline();
 
        collapse.getKeyFrames().add(new KeyFrame(Duration.ZERO,
            new KeyValue(shift, shift.get()),
            new KeyValue(editButton.opacityProperty(), editButton.getOpacity()),
            new KeyValue(deleteButton.opacityProperty(), deleteButton.getOpacity())));
 
        collapse.getKeyFrames().add(new KeyFrame(ITEM_FADE_OUT,
            new KeyValue(editButton.opacityProperty(), 0.0, Interpolator.LINEAR),
            new KeyValue(deleteButton.opacityProperty(), 0.0, Interpolator.LINEAR)));
 
        collapse.getKeyFrames().add(new KeyFrame(CLOSE,
            new KeyValue(shift, 0.0, EASE_IN)));
 
        collapse.setOnFinished(event -> animation = null);
 
        animation = collapse;
        collapse.play();
    }

    public void snapClosed() 
    { 
        stopAnimation();
        open = false;
        shift.set(0);
        editButton.setOpacity(0);
        deleteButton.setOpacity(0);
        tray.setMouseTransparent(true);
    }

    public void snapOpen() 
    { 
        stopAnimation();
        open = true;
        shift.set(-TRAY_WIDTH);
        editButton.setOpacity(1);
        deleteButton.setOpacity(1);
        tray.setMouseTransparent(false);
    }

    public void stopAnimation()
    {
        if(animation != null)
        {
            animation.stop();
            animation = null;
        }
    }

    @Override
    protected void layoutChildren() 
    { 
        Insets padding = getInsets();
 
        double left = padding.getLeft();
        double top = padding.getTop();
        double width = Math.max(0, getWidth() - left - padding.getRight());
        double height = Math.max(0, getHeight() - top - padding.getBottom());
 
        content.resizeRelocate(left, top, width, height);
        tray.resizeRelocate(left + width, top, TRAY_WIDTH, height);
    }

    @Override
    protected double computePrefHeight(double width) 
    { 
        Insets padding = getInsets();
 
        double inner = width < 0
            ? width
            : Math.max(0, width - padding.getLeft() - padding.getRight());
 
        return padding.getTop() + content.prefHeight(inner) + padding.getBottom();
    }

    @Override
    protected double computeMinHeight(double width)
    {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefWidth(double height)
    {
        return 0;
    }
 
    @Override
    protected double computeMinWidth(double height)
    {
        return 0;
    }
}