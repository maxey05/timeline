package com.emgi.timeline.view.content;

import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
import com.emgi.timeline.domain.content.TextBlock;
import com.emgi.timeline.domain.model.Description;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class BlockRenderer
{
    public static final double MAX_IMAGE_WIDTH = 280;

    private static final String NO_ALT_TEXT = "Image unavailable";

    private final Consumer<URI> linkOpener;

    public BlockRenderer(Consumer<URI> linkOpener)
    {
        this.linkOpener = Objects.requireNonNull(linkOpener, "linkOpener");
    }

    public Node renderAll(Description description)
    {
        Objects.requireNonNull(description, "description");

        List<Node> nodes = new ArrayList<>(description.blocks().size());
        for(ContentBlock block : description.blocks())
        {
            nodes.add(render(block));
        }

        VBox column = new VBox();
        column.getStyleClass().add("detail-blocks");
        column.getChildren().setAll(nodes);
        return column;
    }

    public Node render(ContentBlock block)
    {
        Objects.requireNonNull(block, "block");

        return switch(block)
        {
            case TextBlock text -> renderText(text);
            case LinkBlock link -> renderLink(link);
            case ImageBlock image -> renderImage(image);
        };
    }

    private static Node renderText(TextBlock block)
    {
        Label label = new Label(block.text());
        label.getStyleClass().add("block-text");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Node renderLink(LinkBlock block)
    {
        Hyperlink link = new Hyperlink(block.label());
        link.getStyleClass().add("block-link");
        link.setWrapText(true);
        link.setMaxWidth(Double.MAX_VALUE);
        link.setOnAction(event -> linkOpener.accept(block.target()));
        return link;
    }

    private static Node renderImage(ImageBlock block)
    {
        Image image;
        try
        {
            image = new Image(block.source().toString(), true);
        }
        catch(IllegalArgumentException | NullPointerException e)
        {
            return placeholder(block);
        }

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(MAX_IMAGE_WIDTH);

        VBox holder = new VBox(view);
        holder.getStyleClass().add("block-image-holder");

        if(image.isError())
        {
            holder.getChildren().setAll(placeholder(block));
        }
        else
        {
            image.errorProperty().addListener((observable, previous, failed) ->
            {
                if(Boolean.TRUE.equals(failed))
                {
                    holder.getChildren().setAll(placeholder(block));
                }
            });
        }

        return holder;
    }

    private static Node placeholder(ImageBlock block)
    {
        String caption = block.altText().isBlank() ? NO_ALT_TEXT : block.altText();

        Label label = new Label(caption);
        label.getStyleClass().add("block-image-missing");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
