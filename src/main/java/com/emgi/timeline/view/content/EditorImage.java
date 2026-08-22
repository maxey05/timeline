package com.emgi.timeline.view.content;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.net.URI;
import java.util.Objects;

public final class EditorImage
{
    public static final double MAX_WIDTH = 320;

    public static final char PLACEHOLDER = '\uFFFC';

    public static final EditorImage EMPTY = new EditorImage(null);

    private static final String UNAVAILABLE = "Image unavailable";

    private final URI source;

    private EditorImage(URI source)
    {
        this.source = source;
    }

    public static EditorImage of(URI source)
    {
        return new EditorImage(Objects.requireNonNull(source, "source"));
    }

    public boolean isEmpty()
    {
        return source == null;
    }

    public URI source()
    {
        return source;
    }

    public Node createNode()
    {
        if(isEmpty())
        {
            return new Region();
        }

        Image image;
        try
        {
            image = new Image(source.toString(), true);
        }
        catch(IllegalArgumentException | NullPointerException e)
        {
            return placeholder();
        }

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(MAX_WIDTH);

        StackPane holder = new StackPane(view);
        holder.getStyleClass().add("description-image");
        holder.setAlignment(Pos.CENTER);

        if(image.isError())
        {
            holder.getChildren().setAll(placeholder());
        }
        else
        {
            image.errorProperty().addListener((observable, previous, failed) ->
            {
                if(Boolean.TRUE.equals(failed))
                {
                    holder.getChildren().setAll(placeholder());
                }
            });
        }

        return holder;
    }

    private static Node placeholder()
    {
        Label label = new Label(UNAVAILABLE);
        label.getStyleClass().add("description-image-missing");
        return label;
    }

    @Override
    public boolean equals(Object other)
    {
        return other instanceof EditorImage image && Objects.equals(source, image.source);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(source);
    }

    @Override
    public String toString()
    {
        return isEmpty() ? "EditorImage.EMPTY" : "EditorImage[" + source + "]";
    }
}
