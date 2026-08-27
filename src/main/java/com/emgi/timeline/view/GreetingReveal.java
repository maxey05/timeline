package com.emgi.timeline.view;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GreetingReveal
{
    public static final Duration LETTER_DURATION = Duration.millis(500);
    public static final Duration LETTER_STAGGER = Duration.millis(50);
    public static final Duration NAME_DELAY = Duration.millis(300);
    public static final double RISE = 7.0;

    private static final String LETTER_STYLE_CLASS = "app-title-letter";
    private static final Interpolator EASE = Interpolator.SPLINE(0.16, 1.0, 0.3, 1.0);

    private final TextFlow flow;
    private final List<Text> letters = new ArrayList<>();

    private int nameStart;
    private boolean armed;
    private boolean enabled = true;
    private ParallelTransition running;

    public GreetingReveal(TextFlow flow)
    {
        this.flow = Objects.requireNonNull(flow, "flow");
        this.flow.setMinWidth(Region.USE_PREF_SIZE);
    }

    public void setGreeting(String head, String tail)
    {
        stop();

        String text = head + tail;
        nameStart = head.length();

        letters.clear();

        for(int i = 0; i < text.length(); i++)
        {
            Text letter = new Text(text.substring(i, i + 1));
            letter.getStyleClass().add(LETTER_STYLE_CLASS);
            letters.add(letter);
        }

        flow.getChildren().setAll(letters);
        flow.setAccessibleText(text);

        if(armed)
        {
            hide();
        }
        else
        {
            show();
        }
    }

    /**
     * Turns the reveal on or off. When off, {@link #arm()} and {@link #play()} leave the
     * greeting fully visible instead of hiding and animating it. Switching it off while a
     * reveal is running snaps that reveal to its finished state.
     */
    public void setEnabled(boolean value)
    {
        if(enabled == value)
        {
            return;
        }

        enabled = value;

        if(!enabled)
        {
            armed = false;
            stop();
        }
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void arm()
    {
        if(!enabled)
        {
            armed = false;
            show();
            return;
        }

        armed = true;
        hide();
    }

    public void play()
    {
        armed = false;

        if(letters.isEmpty())
        {
            return;
        }

        if(!enabled)
        {
            stop();
            return;
        }

        stop();

        ParallelTransition reveal = new ParallelTransition();

        for(int i = 0; i < letters.size(); i++)
        {
            Text letter = letters.get(i);
            letter.setOpacity(0.0);
            letter.setTranslateY(RISE);

            Duration delay = LETTER_STAGGER.multiply(i);

            if(i >= nameStart)
            {
                delay = delay.add(NAME_DELAY);
            }

            FadeTransition fade = new FadeTransition(LETTER_DURATION, letter);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.setInterpolator(EASE);

            TranslateTransition rise = new TranslateTransition(LETTER_DURATION, letter);
            rise.setFromY(RISE);
            rise.setToY(0.0);
            rise.setInterpolator(EASE);

            ParallelTransition letterReveal = new ParallelTransition(fade, rise);
            letterReveal.setDelay(delay);

            reveal.getChildren().add(letterReveal);
        }

        reveal.setOnFinished(event -> running = null);
        running = reveal;
        reveal.play();
    }

    public void stop()
    {
        if(running != null)
        {
            running.stop();
            running = null;
        }

        show();
    }

    private void show()
    {
        for(Text letter : letters)
        {
            letter.setOpacity(1.0);
            letter.setTranslateY(0.0);
        }
    }

    private void hide()
    {
        for(Text letter : letters)
        {
            letter.setOpacity(0.0);
            letter.setTranslateY(RISE);
        }
    }
}