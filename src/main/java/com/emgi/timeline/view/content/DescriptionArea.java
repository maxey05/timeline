package com.emgi.timeline.view.content;

import com.emgi.timeline.domain.content.DescriptionParser;
import com.emgi.timeline.domain.content.DescriptionSegment;
import com.emgi.timeline.domain.content.ImageSegment;
import com.emgi.timeline.domain.content.ParagraphSegment;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.TextExt;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledSegment;
import org.fxmisc.richtext.model.TextOps;
import org.reactfx.util.Either;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public final class DescriptionArea
        extends GenericStyledArea<String, Either<String, EditorImage>, String>
{
    public static final String CENTERED = "centered";

    public static final String PLAIN = "";

    private static final TextOps<String, String> TEXT_OPS = SegmentOps.styledTextOps();

    private static final EditorImageOps<String> IMAGE_OPS = new EditorImageOps<>();

    private static final TextOps<Either<String, EditorImage>, String> SEGMENT_OPS =
            TEXT_OPS._or(IMAGE_OPS, (left, right) -> Optional.empty());

    public DescriptionArea()
    {
        super(PLAIN,
              DescriptionArea::applyParagraphStyle,
              PLAIN,
              SEGMENT_OPS,
              DescriptionArea::createSegmentNode);

        setWrapText(true);
        getStyleClass().add("description-area");
    }


    private static void applyParagraphStyle(TextFlow paragraph, String style)
    {
        paragraph.setTextAlignment(CENTERED.equals(style) ? TextAlignment.CENTER
                                                          : TextAlignment.LEFT);
    }

    private static Node createSegmentNode(
            StyledSegment<Either<String, EditorImage>, String> segment)
    {
        return segment.getSegment().unify(
                DescriptionArea::createTextNode,
                EditorImage::createNode);
    }

    private static Node createTextNode(String text)
    {
        TextExt node = new TextExt(text);
        node.setTextOrigin(VPos.TOP);
        node.getStyleClass().add("text");
        return node;
    }


    public void load(String description)
    {
        clear();

        boolean first = true;

        for(DescriptionSegment segment : DescriptionParser.parse(description))
        {
            if(!first)
            {
                appendText("\n");
            }
            first = false;

            if(segment instanceof ParagraphSegment paragraph)
            {
                appendText(paragraph.plainText());
            }
            else if(segment instanceof ImageSegment image)
            {
                insert(getLength(), documentFor(EditorImage.of(image.source())));
            }
        }

        restyleParagraphs();
        moveTo(0);
    }

    public String describedText()
    {
        StringBuilder text = new StringBuilder();

        List<Paragraph<String, Either<String, EditorImage>, String>> paragraphs =
                getParagraphs();

        for(int index = 0; index < paragraphs.size(); index++)
        {
            if(index > 0)
            {
                endLine(text);
            }

            appendParagraph(text, paragraphs.get(index));
        }

        return text.toString();
    }

    private static void appendParagraph(
            StringBuilder text,
            Paragraph<String, Either<String, EditorImage>, String> paragraph)
    {
        for(Either<String, EditorImage> segment : paragraph.getSegments())
        {
            if(segment.isLeft())
            {
                text.append(segment.getLeft());
                continue;
            }

            EditorImage image = segment.getRight();

            if(image.isEmpty())
            {
                continue;
            }

            endLine(text);
            text.append(DescriptionParser.imageToken(image.source(), ""));
            text.append('\n');
        }
    }

    private static void endLine(StringBuilder text)
    {
        if(text.length() > 0 && text.charAt(text.length() - 1) != '\n')
        {
            text.append('\n');
        }
    }


    public void insertImages(List<URI> sources)
    {
        for(URI source : sources)
        {
            if(getCaretColumn() > 0)
            {
                insertText(getCaretPosition(), "\n");
            }

            int position = getCaretPosition();
            insert(position, documentFor(EditorImage.of(source)));
            moveTo(position + 1);

            insertText(getCaretPosition(), "\n");
        }

        restyleParagraphs();
        requestFollowCaret();
    }

    private ReadOnlyStyledDocument<String, Either<String, EditorImage>, String> documentFor(
            EditorImage image)
    {
        return ReadOnlyStyledDocument.fromSegment(
                Either.right(image), PLAIN, PLAIN, getSegOps());
    }

    public void restyleParagraphs()
    {
        int count = getParagraphs().size();

        for(int index = 0; index < count; index++)
        {
            Paragraph<String, Either<String, EditorImage>, String> paragraph =
                    getParagraph(index);

            String style = holdsImage(paragraph) ? CENTERED : PLAIN;

            if(!style.equals(paragraph.getParagraphStyle()))
            {
                setParagraphStyle(index, style);
            }
        }
    }

    private static boolean holdsImage(
            Paragraph<String, Either<String, EditorImage>, String> paragraph)
    {
        for(Either<String, EditorImage> segment : paragraph.getSegments())
        {
            if(segment.isRight() && !segment.getRight().isEmpty())
            {
                return true;
            }
        }

        return false;
    }
}
