package com.emgi.timeline.view;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Objects;

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
