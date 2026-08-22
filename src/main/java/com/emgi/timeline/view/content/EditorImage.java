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

/**
 * One picture, as it exists inside the editor's document.
 *
 * <p>This is the editor-side twin of {@link com.emgi.timeline.domain.content.ImageSegment}.
 * They are deliberately not the same class: the domain one is derived from stored text and
 * knows nothing about drawing, while this one is a live segment in a rich text document and
 * has to be able to build the Node that stands in for it on screen.
 *
 * <p>To the text model an image is exactly <strong>one character</strong> wide -- U+FFFC,
 * OBJECT REPLACEMENT CHARACTER. That single fact is what makes the caret, selection,
 * Backspace and undo all work on a picture without any special handling: as far as the
 * editor is concerned it is a character that happens to be drawn as a photograph.
 *
 * <p>{@link #EMPTY} is the zero-length segment RichTextFX asks for whenever it needs a
 * placeholder -- splitting a paragraph, an empty selection, the tail of a subSequence.
 * It draws nothing and measures nothing.
 */
public final class EditorImage
{
    /** How wide a picture may be drawn in the editor before it is scaled down. */
    public static final double MAX_WIDTH = 320;

    /** What the text model sees in place of a picture: one OBJECT REPLACEMENT CHARACTER. */
    public static final char PLACEHOLDER = '\uFFFC';

    /** The zero-length image. Never drawn; exists so the segment type has an identity. */
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

    /**
     * Builds what the reader actually sees where this segment sits.
     *
     * <p>Loaded in the background ({@code new Image(url, true)}) for the reason
     * ARCHITECTURE.md §11 Risk 6 gives: the synchronous constructor freezes the window on a
     * large or slow file, and here it would do it while someone is typing.
     *
     * <p>A broken reference draws a labelled placeholder rather than a blank gap, because
     * the reference-not-embed decision (Risk 3) means a moved or deleted file is a normal
     * thing to happen, not a crash.
     */
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

        /*
         * The ImageView goes in a StackPane rather than straight into the paragraph because
         * a bare ImageView cannot carry padding or a style class the theme can reach, and
         * because swapping in the placeholder on a load failure needs a parent to swap it
         * into -- the failure arrives later than this method returns.
         */
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

    /*
     * Identity is the address and nothing else. RichTextFX compares segments when it decides
     * whether two runs can be merged, so two references to the same file must look equal.
     */
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
