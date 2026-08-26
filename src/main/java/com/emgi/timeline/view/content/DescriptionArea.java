package com.emgi.timeline.view.content;

import com.emgi.timeline.domain.content.BulletSegment;
import com.emgi.timeline.domain.content.DescriptionParser;
import com.emgi.timeline.domain.content.DescriptionSegment;
import com.emgi.timeline.domain.content.DescriptionWriter;
import com.emgi.timeline.domain.content.ImageSegment;
import com.emgi.timeline.domain.content.ParagraphSegment;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.TextExt;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.SegmentOps;
import org.fxmisc.richtext.model.StyledSegment;
import org.fxmisc.richtext.model.TextOps;
import org.reactfx.Subscription;
import org.reactfx.util.Either;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DescriptionArea
        extends GenericStyledArea<String, Either<String, EditorImage>, String>
{
    public static final String CENTERED = "centered";

    public static final String BULLET = "bullet";

    public static final String PLAIN = "";

    private static final String MARKER = "•";

    private static final double MARKER_WIDTH = 18;

    private static final TextOps<String, String> TEXT_OPS = SegmentOps.styledTextOps();

    private static final EditorImageOps<String> IMAGE_OPS = new EditorImageOps<>();

    private static final TextOps<Either<String, EditorImage>, String> SEGMENT_OPS =
            TEXT_OPS._or(IMAGE_OPS, (left, right) -> Optional.empty());

    private static final Runnable NOTHING = () -> { };

    private final Subscription textChanges;

    private Runnable descriptionChanged = NOTHING;

    public DescriptionArea()
    {
        super(PLAIN,
              DescriptionArea::applyParagraphStyle,
              PLAIN,
              SEGMENT_OPS,
              DescriptionArea::createSegmentNode);

        setWrapText(true);
        getStyleClass().add("description-area");
        setParagraphGraphicFactory(this::markerFor);

        addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        addEventFilter(KeyEvent.KEY_TYPED, this::onKeyTyped);

        textChanges = plainTextChanges().subscribe(change -> notifyChanged());
    }

    public void setOnDescriptionChanged(Runnable listener)
    {
        descriptionChanged = listener == null ? NOTHING : listener;
    }

    @Override
    public void dispose()
    {
        descriptionChanged = NOTHING;
        textChanges.unsubscribe();
        super.dispose();
    }

    private void notifyChanged()
    {
        descriptionChanged.run();
    }


    private Node markerFor(int index)
    {
        if(index >= getParagraphs().size()
            || !BULLET.equals(getParagraph(index).getParagraphStyle()))
        {
            Region blank = new Region();
            blank.setMinWidth(0);
            blank.setPrefWidth(0);
            blank.setMaxWidth(0);
            return blank;
        }

        Label marker = new Label(MARKER);
        marker.getStyleClass().add("bullet-marker");
        marker.setAlignment(Pos.TOP_LEFT);
        marker.setMinWidth(MARKER_WIDTH);
        marker.setPrefWidth(MARKER_WIDTH);
        marker.setMaxWidth(MARKER_WIDTH);
        return marker;
    }

    private void applyStyle(int index, String style)
    {
        if(style.equals(getParagraph(index).getParagraphStyle()))
        {
            return;
        }

        setParagraphStyle(index, style);
        recreateParagraphGraphic(index);
        notifyChanged();
    }


    private void onKeyPressed(KeyEvent event)
    {
        if(event.isShortcutDown() || event.isAltDown())
        {
            return;
        }

        if(event.getCode() == KeyCode.ENTER)
        {
            onEnter(event);
        }
        else if(event.getCode() == KeyCode.BACK_SPACE)
        {
            onBackspace(event);
        }
    }

    private void onEnter(KeyEvent event)
    {
        int index = getCurrentParagraph();

        if(BULLET.equals(getParagraph(index).getParagraphStyle()))
        {
            if(getParagraph(index).getText().isEmpty())
            {
                applyStyle(index, PLAIN);
                requestFollowCaret();
                event.consume();
                return;
            }

            replaceSelection("\n");
            applyStyle(getCurrentParagraph(), BULLET);
            restyleParagraphs();
            requestFollowCaret();
            event.consume();
            return;
        }

        replaceSelection("\n");
        restyleParagraphs();
        requestFollowCaret();
        event.consume();
    }

    private void onBackspace(KeyEvent event)
    {
        if(event.isShiftDown() || getSelection().getLength() > 0 || getCaretColumn() != 0)
        {
            return;
        }

        int index = getCurrentParagraph();

        if(!BULLET.equals(getParagraph(index).getParagraphStyle()))
        {
            return;
        }

        applyStyle(index, PLAIN);
        event.consume();
    }

    private void onKeyTyped(KeyEvent event)
    {
        if(event.isShortcutDown() || event.isAltDown()
            || !" ".equals(event.getCharacter())
            || getSelection().getLength() > 0
            || getCaretColumn() != 1)
        {
            return;
        }

        int index = getCurrentParagraph();

        if(!"-".equals(getParagraph(index).getText()))
        {
            return;
        }

        int start = getAbsolutePosition(index, 0);

        deleteText(start, start + 1);
        applyStyle(index, BULLET);
        requestFollowCaret();
        event.consume();
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
            else if(segment instanceof BulletSegment bullet)
            {
                appendText(bullet.plainText());
                setParagraphStyle(getParagraphs().size() - 1, BULLET);
            }
            else if(segment instanceof ImageSegment image)
            {
                insert(getLength(), documentFor(EditorImage.of(image.source())));
            }
        }

        restyleParagraphs();

        for(int index = 0; index < getParagraphs().size(); index++)
        {
            recreateParagraphGraphic(index);
        }

        moveTo(0);
    }

    public String describedText()
    {
        List<Paragraph<String, Either<String, EditorImage>, String>> paragraphs =
                getParagraphs();

        List<DescriptionWriter.Line> lines = new ArrayList<>(paragraphs.size());

        for(Paragraph<String, Either<String, EditorImage>, String> paragraph : paragraphs)
        {
            lines.add(lineFor(paragraph));
        }

        return DescriptionWriter.write(lines);
    }

    private static DescriptionWriter.Line lineFor(
            Paragraph<String, Either<String, EditorImage>, String> paragraph)
    {
        List<DescriptionWriter.Piece> pieces = new ArrayList<>();

        for(Either<String, EditorImage> segment : paragraph.getSegments())
        {
            if(segment.isLeft())
            {
                pieces.add(new DescriptionWriter.Words(segment.getLeft()));
                continue;
            }

            EditorImage image = segment.getRight();

            if(!image.isEmpty())
            {
                pieces.add(new DescriptionWriter.Picture(image.source()));
            }
        }

        return new DescriptionWriter.Line(
                BULLET.equals(paragraph.getParagraphStyle()), pieces);
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

            String current = paragraph.getParagraphStyle();

            String style = holdsImage(paragraph) ? CENTERED
                         : BULLET.equals(current) ? BULLET
                         : PLAIN;

            applyStyle(index, style);
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
