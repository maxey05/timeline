package com.emgi.timeline.view;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Objects;

/**
 * The single place that knows what Timeline is dressed in: which stylesheets a window wears,
 * and which font faces the application ships with.
 *
 * <p>This is view-layer only. It carries no rules, no state, and nothing below it depends on
 * it — deleting this class would cost the application its looks and nothing else.</p>
 */
public final class Theme
{
    private static final String CSS_BASE = "/com/emgi/timeline/css/base.css";
    private static final String CSS_THEME = "/com/emgi/timeline/css/theme-mono.css";

    private static final List<String> FONT_FACES = List.of(
        "/com/emgi/timeline/fonts/Arimo-Regular.ttf",
        "/com/emgi/timeline/fonts/Arimo-Bold.ttf",
        "/com/emgi/timeline/fonts/Arimo-Italic.ttf",
        "/com/emgi/timeline/fonts/Arimo-BoldItalic.ttf"
    );

    private Theme()
    {
    }

    /**
     * Registers the bundled Arimo faces with the JavaFX font system, so that
     * {@code -fx-font-family: "Arimo"} resolves on a machine that has never installed it.
     *
     * <p>Call once, before the first scene is built. A face that will not load is reported on
     * stderr and skipped: the fallback stack in base.css takes over, which is a worse-looking
     * application but still a working one.</p>
     */
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

    public static void applyTo(Scene scene)
    {
        Objects.requireNonNull(scene, "scene").getStylesheets().setAll(stylesheets());
    }

    /**
     * An {@code Alert} owns its own {@code Stage} and {@code Scene}, so it does not inherit the
     * stylesheets of the window that opened it. Without this call every confirmation and error
     * in the application renders in Modena's light default — white panels out of a dark app.
     */
    public static void applyTo(Dialog<?> dialog)
    {
        Objects.requireNonNull(dialog, "dialog")
            .getDialogPane()
            .getStylesheets()
            .setAll(stylesheets());
    }

    private static List<String> stylesheets()
    {
        return List.of(
            resource(CSS_BASE).toExternalForm(),
            resource(CSS_THEME).toExternalForm());
    }

    private static URL resource(String path)
    {
        return Objects.requireNonNull(
            Theme.class.getResource(path), "Missing classpath resource: " + path
        );
    }
}
