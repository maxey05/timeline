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

/**
 * The description writing surface: one continuous document in which pictures are characters.
 *
 * <h2>Why this class exists at all</h2>
 *
 * <p>It replaced a {@code TextArea}, which could never have done this job. A TextArea is a
 * plain-text control -- its skin lays the whole content out as a single uniform run, with no
 * notion of a styled span and no way to host a Node -- so an inserted picture could only
 * ever appear as the literal text {@code ![](file:///...)}. Showing the picture itself means
 * replacing the control, and this is that replacement.
 *
 * <h2>What it does NOT change</h2>
 *
 * <p><strong>Nothing below the view layer.</strong> A description is still one string, still
 * stored in one column, still parsed by {@link DescriptionParser}. This class loads that
 * string into a document ({@link #load}) and writes a document back out as that same string
 * ({@link #describedText}). The domain, the schema and the repository are untouched and do
 * not know that a rich editor exists.
 *
 * <h2>The three type parameters</h2>
 *
 * <p>{@code GenericStyledArea<PS, SEG, S>} is generic in paragraph style, segment and text
 * style. Here they are:
 *
 * <ul>
 *   <li><b>PS = String</b> -- {@link #CENTERED} or {@link #PLAIN}. A paragraph holding a
 *       picture is centred; every other paragraph is left-aligned.</li>
 *   <li><b>SEG = Either&lt;String, EditorImage&gt;</b> -- a segment is a run of text
 *       <em>or</em> a picture. This is the whole trick: {@link EditorImageOps} makes the
 *       picture behave as one character, so the caret, Backspace, selection and undo all
 *       work on it without a line of special-case code.</li>
 *   <li><b>S = String</b> -- unused. There is no bold, no italic, no colour; the description
 *       has exactly one text style. It stays a parameter because the API requires one.</li>
 * </ul>
 *
 * <h2>Images own their line</h2>
 *
 * <p>Enforced in three places, because one is not enough: {@link #insertImages} breaks the
 * paragraph before and after the picture, {@link #restyleParagraphs} centres exactly those
 * paragraphs that hold one, and {@link #describedText} puts the token on a line of its own
 * no matter what the document looks like. That last one is not belt-and-braces -- it is
 * required. {@link DescriptionParser} only recognises an image token that occupies a whole
 * line, so a token serialised beside text would be read back as literal text.
 */
public final class DescriptionArea
        extends GenericStyledArea<String, Either<String, EditorImage>, String>
{
    /** Paragraph style for a paragraph that holds a picture. */
    public static final String CENTERED = "centered";

    /** Paragraph style for everything else, and the only text style there is. */
    public static final String PLAIN = "";

    private static final TextOps<String, String> TEXT_OPS = SegmentOps.styledTextOps();

    private static final EditorImageOps<String> IMAGE_OPS = new EditorImageOps<>();

    /*
     * The combined operations for "a segment is text or a picture". Everything RichTextFX
     * does to the document -- splitting, joining, measuring, undoing -- goes through here.
     */
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

    // ---------------------------------------------------------------- rendering

    /**
     * Centres a paragraph that holds a picture, left-aligns everything else.
     *
     * <p>Alignment is a property of the paragraph's TextFlow, which is why "on its own line,
     * centred" has to be a <em>paragraph</em> style rather than something set on the image
     * node. An ImageView centred inside itself is still sitting at the left of its line.
     */
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

    /*
     * TextExt rather than Text: RichTextFX measures and hit-tests its own subclass, and a
     * plain Text node puts the caret in the wrong place. TOP origin is what makes a tall
     * segment (a picture) and a short one (a line of words) share a sane baseline.
     */
    private static Node createTextNode(String text)
    {
        TextExt node = new TextExt(text);
        node.setTextOrigin(VPos.TOP);
        node.getStyleClass().add("text");
        return node;
    }

    // ---------------------------------------------------------------- loading

    /**
     * Replaces the whole document with the parsed form of {@code description}.
     *
     * <p>Goes through {@link DescriptionParser} rather than reading the string directly, so
     * the editor and the detail panel agree on what counts as an image by construction --
     * there is only one parser and this is it.
     */
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

    /**
     * Serialises the document back to the one string the domain stores.
     *
     * <p>The newline bookkeeping is the point of this method. A picture is written as a
     * token on a line of its own <em>whatever</em> the document does, because
     * {@link DescriptionParser} only recognises an image token that owns its line -- write
     * one beside a word and it reads back as the literal characters the user was trying to
     * get rid of in the first place.
     */
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

    /** Starts a new line unless one has just been started. Never doubles a newline. */
    private static void endLine(StringBuilder text)
    {
        if(text.length() > 0 && text.charAt(text.length() - 1) != '\n')
        {
            text.append('\n');
        }
    }

    // ---------------------------------------------------------------- inserting

    /**
     * Drops pictures in at the caret, each one alone on a centred line.
     *
     * <p>Three edits per picture, and all three are needed. The leading break gets the
     * caret off a line that already has words on it. The trailing break stops the next
     * thing typed from landing beside the picture. And the paragraph that break creates
     * inherits the centred style from the one it was split off, so the restyle afterwards
     * is what stops everything typed below a picture from being centred too.
     */
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

    /**
     * Re-derives every paragraph's alignment from what that paragraph actually holds.
     *
     * <p>Cheaper to reason about than tracking styles through each edit, and immune to the
     * way RichTextFX copies a paragraph's style into the paragraph a split creates. Called
     * after the two operations that can change the shape of the document; ordinary typing
     * cannot move a picture between paragraphs, so it does not need to run per keystroke.
     */
    public void restyleParagraphs()
    {
        /*
         * getParagraphs() is a LIVE list and setParagraphStyle replaces entries in it, so the
         * count is taken once up front and each paragraph is fetched fresh. A style change
         * cannot add or remove a paragraph, which is what makes the fixed count correct.
         */
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
