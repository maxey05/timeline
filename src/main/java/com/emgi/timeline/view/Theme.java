package com.emgi.timeline.view;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Owns the app's stylesheets and its current light/dark mode.
 *
 * <p>The stylesheet list is layered. base.css carries structure — metrics, radii,
 * spacing — and no colour. theme-mono.css carries the dark token set plus every
 * colour rule in the app, written against those tokens. theme-light.css is
 * appended on top in light mode and redefines only the tokens, which is enough to
 * repaint everything, because JavaFX resolves a looked-up colour at the point of
 * use against whatever the cascade last set it to.
 *
 * <p>Mode is global process state rather than a per-Scene property because Alerts
 * own their own Stage and are built ad hoc all over the app; a static read is the
 * only thing every one of those call sites can reach. Scenes handed to
 * {@link #applyTo(Scene)} are remembered weakly so {@link #setDarkTheme(boolean)}
 * can repaint them in place — that is what makes the settings toggle take effect
 * without a restart.
 */
public final class Theme
{
    private static final String CSS_BASE = "/com/emgi/timeline/css/base.css";
    private static final String CSS_THEME = "/com/emgi/timeline/css/theme-mono.css";
    private static final String CSS_LIGHT = "/com/emgi/timeline/css/theme-light.css";

    private static final List<String> FONT_FACES = List.of(
        "/com/emgi/timeline/fonts/Arimo-Regular.ttf",
        "/com/emgi/timeline/fonts/Arimo-Bold.ttf",
        "/com/emgi/timeline/fonts/Arimo-Italic.ttf",
        "/com/emgi/timeline/fonts/Arimo-BoldItalic.ttf"
    );

    private static final List<WeakReference<Scene>> SCENES = new ArrayList<>();

    private static boolean darkTheme = true;

    private Theme()
    {
    }

    public static void loadFonts()
    {
        for(String face : FONT_FACES)
        {
            try(InputStream stream = Theme.class.getResourceAsStream(face))
            {
                if(stream == null || Font.loadFont(stream, 12) == null)
                {
                    System.err.println(
                        "Timeline: could not load the bundled font " + face
                        + " — falling back to the system sans-serif.");
                }
            }
            catch(IOException e)
            {
                System.err.println(
                    "Timeline: could not read the bundled font " + face + " — " + e.getMessage());
            }
        }
    }

    public static boolean isDarkTheme()
    {
        return darkTheme;
    }

    /**
     * Switches the palette and repaints every Scene that has been through
     * {@link #applyTo(Scene)} and is still alive. Dialogs opened after this call pick
     * the new mode up on their own; dialogs already on screen do not, which is
     * harmless because they are modal and the settings panel is behind them.
     *
     * <p>Call this before the first {@code applyTo} at startup to avoid a flash of the
     * wrong theme.
     */
    public static void setDarkTheme(boolean value)
    {
        if(darkTheme == value)
        {
            return;
        }

        darkTheme = value;

        repaintScenes();
    }

    public static void applyTo(Scene scene)
    {
        Objects.requireNonNull(scene, "scene").getStylesheets().setAll(stylesheets());

        remember(scene);
    }

    public static void applyTo(Dialog<?> dialog)
    {
        Objects.requireNonNull(dialog, "dialog")
            .getDialogPane()
            .getStylesheets()
            .setAll(stylesheets());
    }

    private static void repaintScenes()
    {
        List<String> sheets = stylesheets();

        for(Iterator<WeakReference<Scene>> it = SCENES.iterator(); it.hasNext(); )
        {
            Scene scene = it.next().get();

            if(scene == null)
            {
                it.remove();
                continue;
            }

            /*
             * setAll on an unchanged prefix is a no-op as far as JavaFX's CSS pass is
             * concerned, so clear first. That forces a full re-resolution of every
             * looked-up colour rather than leaving stale values on the nodes.
             */
            scene.getStylesheets().clear();
            scene.getStylesheets().setAll(sheets);
        }
    }

    private static void remember(Scene scene)
    {
        for(Iterator<WeakReference<Scene>> it = SCENES.iterator(); it.hasNext(); )
        {
            Scene known = it.next().get();

            if(known == null)
            {
                it.remove();
            }
            else if(known == scene)
            {
                return;
            }
        }

        SCENES.add(new WeakReference<>(scene));
    }

    private static List<String> stylesheets()
    {
        String base = resource(CSS_BASE).toExternalForm();
        String theme = resource(CSS_THEME).toExternalForm();

        return darkTheme
            ? List.of(base, theme)
            : List.of(base, theme, resource(CSS_LIGHT).toExternalForm());
    }

    private static URL resource(String path)
    {
        return Objects.requireNonNull(
            Theme.class.getResource(path), "Missing classpath resource: " + path
        );
    }
}
