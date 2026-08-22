package com.emgi.timeline.view.content;

import com.emgi.timeline.domain.content.DescriptionParser;
import com.emgi.timeline.domain.content.DescriptionSegment;
import com.emgi.timeline.domain.content.ImageSegment;
import com.emgi.timeline.domain.content.ParagraphSegment;
import com.emgi.timeline.domain.content.TextRun;
import com.emgi.timeline.domain.model.Description;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Draws a description into the detail panel.
 *
 * <p>Replaces BlockRenderer. The difference is where the structure comes from: it used to
 * be handed a typed list the domain had stored, and now it asks {@link DescriptionParser}
 * to derive one from a string. Everything below that line is the same job.
 *
 * <p>A paragraph becomes a TextFlow rather than a Label because a Label cannot hold a
 * clickable Hyperlink partway through its own text -- which is exactly what "paste a link
 * into a sentence" needs.
 */
public final class DescriptionRenderer
{
    public static final double MAX_IMAGE_WIDTH = 280;

    private static final String NO_ALT_TEXT = "Image unavailable";

    private final Consumer<URI> linkOpener;

    public DescriptionRenderer(Consumer<URI> linkOpener)
    {
        this.linkOpener = Objects.requireNonNull(linkOpener, "linkOpener");
    }

    public Node renderAll(Description description)
    {
        Objects.requireNonNull(description, "description");

        List<DescriptionSegment> segments = DescriptionParser.parse(description.text());
        List<Node> nodes = new ArrayList<>(segments.size());

        for(DescriptionSegment segment : segments)
        {
            nodes.add(render(segment));
        }

        VBox column = new VBox();
        column.getStyleClass().add("description-body");
        column.getChildren().setAll(nodes);
        return column;
    }

    public Node render(DescriptionSegment segment)
    {
        Objects.requireNonNull(segment, "segment");

        return switch(segment)
        {
            case ParagraphSegment paragraph -> renderParagraph(paragraph);
            case ImageSegment image -> renderImage(image);
        };
    }

    private Node renderParagraph(ParagraphSegment paragraph)
    {
        List<Node> pieces = new ArrayList<>(paragraph.runs().size());

        for(TextRun run : paragraph.runs())
        {
            pieces.add(run.isLink() ? linkFor(run) : textFor(run));
        }

        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("description-paragraph");
        flow.getChildren().setAll(pieces);
        flow.setMaxWidth(Double.MAX_VALUE);
        return flow;
    }

    private static Node textFor(TextRun run)
    {
        Text text = new Text(run.text());
        text.getStyleClass().add("description-text");
        return text;
    }

    private Node linkFor(TextRun run)
    {
        Hyperlink link = new Hyperlink(run.text());
        link.getStyleClass().add("description-link");
        link.setOnAction(event -> linkOpener.accept(run.target()));
        return link;
    }

    private static Node renderImage(ImageSegment segment)
    {
        Image image;
        try
        {
            image = new Image(segment.source().toString(), true);
        }
        catch(IllegalArgumentException | NullPointerException e)
        {
            return placeholder(segment);
        }

        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(MAX_IMAGE_WIDTH);

        VBox holder = new VBox(view);
        holder.getStyleClass().add("description-image");

        if(image.isError())
        {
            holder.getChildren().setAll(placeholder(segment));
        }
        else
        {
            image.errorProperty().addListener((observable, previous, failed) ->
            {
                if(Boolean.TRUE.equals(failed))
                {
                    holder.getChildren().setAll(placeholder(segment));
                }
            });
        }

        return holder;
    }

    private static Node placeholder(ImageSegment segment)
    {
        String caption = segment.altText().isBlank() ? NO_ALT_TEXT : segment.altText();

        Label label = new Label(caption);
        label.getStyleClass().add("description-image-missing");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }
}
