package com.emgi.timeline.view;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleRole;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

public final class ToggleSwitch extends Pane
{
    public static final double TRACK_WIDTH = 36;
    public static final double TRACK_HEIGHT = 20;

    private static final double KNOB_SIZE = 14;
    private static final double KNOB_INSET = 3;
    private static final double TRAVEL = TRACK_WIDTH - KNOB_SIZE - (KNOB_INSET * 2);

    private static final Duration SLIDE = Duration.millis(120);

    private static final PseudoClass SELECTED_CLASS = PseudoClass.getPseudoClass("selected");

    private final Region trackOff = new Region();
    private final Region trackOn = new Region();
    private final StackPane knobBox = new StackPane();
    private final Region knobOff = new Region();
    private final Region knobOn = new Region();

    private final Timeline slide = new Timeline();

    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", true);

    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", false)
    {
        @Override
        protected void invalidated()
        {
            pseudoClassStateChanged(SELECTED_CLASS, get());
            apply(get(), animated.get());
        }
    };

    public ToggleSwitch()
    {
        getStyleClass().add("switch");

        trackOff.getStyleClass().add("switch-track");
        trackOn.getStyleClass().add("switch-track-on");
        knobBox.getStyleClass().add("switch-knob-box");
        knobOff.getStyleClass().add("switch-knob");
        knobOn.getStyleClass().add("switch-knob-on");

        trackOff.setManaged(false);
        trackOn.setManaged(false);
        knobBox.setManaged(false);

        trackOn.setOpacity(0);
        knobOn.setOpacity(0);

        knobOff.setPrefSize(KNOB_SIZE, KNOB_SIZE);
        knobOff.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        knobOff.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        knobOn.setPrefSize(KNOB_SIZE, KNOB_SIZE);
        knobOn.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        knobOn.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        knobBox.getChildren().setAll(knobOff, knobOn);
        getChildren().setAll(trackOff, trackOn, knobBox);

        setPrefSize(TRACK_WIDTH, TRACK_HEIGHT);
        setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);

        setOnMouseClicked(event ->
        {
            if(event.getButton() != MouseButton.PRIMARY)
            {
                return;
            }

            requestFocus();
            toggle();
            event.consume();
        });

        setOnKeyPressed(event ->
        {
            if(event.getCode() == KeyCode.SPACE)
            {
                toggle();
                event.consume();
            }
        });
    }

    public void toggle()
    {
        selected.set(!selected.get());
    }

    public void settle()
    {
        apply(selected.get(), false);
    }

    private void apply(boolean on, boolean animate)
    {
        slide.stop();

        double x = on ? TRAVEL : 0;
        double opacity = on ? 1 : 0;

        if(!animate)
        {
            knobBox.setTranslateX(x);
            trackOn.setOpacity(opacity);
            knobOn.setOpacity(opacity);
            return;
        }

        slide.getKeyFrames().setAll(new KeyFrame(SLIDE,
            new KeyValue(knobBox.translateXProperty(), x, Interpolator.EASE_BOTH),
            new KeyValue(trackOn.opacityProperty(), opacity, Interpolator.EASE_BOTH),
            new KeyValue(knobOn.opacityProperty(), opacity, Interpolator.EASE_BOTH)));

        slide.playFromStart();
    }

    @Override
    protected void layoutChildren()
    {
        trackOff.resizeRelocate(0, 0, TRACK_WIDTH, TRACK_HEIGHT);
        trackOn.resizeRelocate(0, 0, TRACK_WIDTH, TRACK_HEIGHT);
        knobBox.resizeRelocate(KNOB_INSET, KNOB_INSET, KNOB_SIZE, KNOB_SIZE);
    }

    @Override
    protected double computePrefWidth(double height)
    {
        return TRACK_WIDTH;
    }

    @Override
    protected double computePrefHeight(double width)
    {
        return TRACK_HEIGHT;
    }

    public BooleanProperty selectedProperty()
    {
        return selected;
    }

    public boolean isSelected()
    {
        return selected.get();
    }

    public void setSelected(boolean value)
    {
        selected.set(value);
    }

    public BooleanProperty animatedProperty()
    {
        return animated;
    }

    public boolean isAnimated()
    {
        return animated.get();
    }

    public void setAnimated(boolean value)
    {
        animated.set(value);
    }
}