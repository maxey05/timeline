package com.emgi.timeline.controller;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.TextBlock;
import com.emgi.timeline.domain.model.Description;
import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.domain.validation.IdeaValidator;
import com.emgi.timeline.domain.validation.ValidationResult;
import com.emgi.timeline.service.IdeaService;
import com.emgi.timeline.service.SaveOutcome;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The MVC controller behind the editor dialog (ARCHITECTURE.md §7.1–7.2).
 *
 * <p>Holds the <em>mutable</em> form model that §7.2 calls for, as bindable properties, so the
 * stored {@link Idea} is never touched while the user types. Cancel is therefore free: throw the
 * controller away and nothing happened.
 *
 * <p>One controller instance per opened dialog. It is not reusable across dialogs and does not try
 * to be — {@code IdeaEditorDialog} constructs a fresh one each time.
 *
 * <p><strong>Phase 4 constraint:</strong> a description is one text block. {@code TextArea} in, one
 * {@link TextBlock} out. Phase 6 replaces {@link #descriptionFromForm()} and {@link #textOf} with
 * the block editor; nothing else here changes.
 */
public final class IdeaEditorController
{
    /** What {@link #save()} did, in terms the view can act on without knowing the service. */
    public enum SaveResult
    {
        /** Persisted. {@link #savedIdea()} holds the result; close the dialog. */
        SAVED,

        /** Rejected. The error properties are populated; keep the dialog open. */
        INVALID,

        /** The idea being edited no longer exists in storage. Tell the user; close; refresh. */
        MISSING
    }

    /** Separator when several text blocks are flattened into the single Phase 4 text area. */
    private static final String BLOCK_SEPARATOR = "\n\n";

    private final IdeaService service;

    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty descriptionText = new SimpleStringProperty("");
    private final ObjectProperty<IdeaStatus> status =
            new SimpleObjectProperty<>(IdeaStatus.INCOMPLETE);
    private final ObservableList<Tag> tags = FXCollections.observableArrayList();

    private final ReadOnlyStringWrapper titleError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper descriptionError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper tagsError = new ReadOnlyStringWrapper("");

    /** Null in create mode; the id being edited otherwise. This field <em>is</em> the mode. */
    private IdeaId editingId;

    private Idea savedIdea;

    private boolean targetMissing;

    public IdeaEditorController(IdeaService service)
    {
        this.service = Objects.requireNonNull(service, "service");
    }

    // ---- form model ----------------------------------------------------------------

    public StringProperty titleProperty()
    {
        return title;
    }

    public StringProperty descriptionTextProperty()
    {
        return descriptionText;
    }

    public ObjectProperty<IdeaStatus> statusProperty()
    {
        return status;
    }

    /** Live, order-preserving view of the chips. The view rebuilds on change; it never edits. */
    public ObservableList<Tag> tags()
    {
        return tags;
    }

    public ReadOnlyStringProperty titleErrorProperty()
    {
        return titleError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty descriptionErrorProperty()
    {
        return descriptionError.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty tagsErrorProperty()
    {
        return tagsError.getReadOnlyProperty();
    }

    public boolean isEditing()
    {
        return editingId != null;
    }

    /** The persisted idea, once {@link #save()} has returned {@code SAVED}. Empty until then. */
    public Optional<Idea> savedIdea()
    {
        return Optional.ofNullable(savedIdea);
    }

    /**
     * Whether the last {@link #save()} found the edited idea gone from storage.
     *
     * <p>This is what tells the caller "the row on screen is a ghost, reload" apart from the far
     * commoner "the user pressed Cancel", without making it reload after every cancel.
     */
    public boolean targetMissing()
    {
        return targetMissing;
    }

    // ---- opening -------------------------------------------------------------------

    /** Empty form: no title, no tags, no description, status Incomplete. */
    public void beginCreate()
    {
        editingId = null;
        savedIdea = null;
        targetMissing = false;

        title.set("");
        descriptionText.set("");
        status.set(IdeaStatus.INCOMPLETE);
        tags.clear();

        clearErrors();
    }

    /**
     * Loads an existing idea into the form. The argument is never mutated and never stored — only
     * its id is kept, so the save path re-reads storage rather than trusting a stale copy.
     */
    public void beginEdit(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");

        editingId = idea.id();
        savedIdea = null;
        targetMissing = false;

        title.set(idea.title());
        descriptionText.set(textOf(idea.description()));
        status.set(idea.status());

        // Sorted, because Set iteration order is arbitrary and the chips would otherwise appear
        // in a different order each time the same idea is opened (same reason IdeaListCell sorts).
        List<Tag> ordered = new ArrayList<>(idea.tags());
        ordered.sort(Comparator.comparing(Tag::name));
        tags.setAll(ordered);

        clearErrors();
    }

    // ---- tag entry -----------------------------------------------------------------

    /**
     * Parses one line of raw tag text the user typed and adds it.
     *
     * <p>This is the form-input concern {@code IdeaValidator}'s javadoc hands to Phase 4: a
     * {@link Tag} cannot exist in an invalid state, so {@code Tag.of} throws on blank or
     * over-length input, and an exception out of a keystroke is not an acceptable UI. It becomes a
     * message under the tag field instead.
     *
     * <p>Adding a tag the form already holds is a silent no-op, not an error — {@code "Java"} and
     * {@code " java "} canonicalize to the same tag, and telling the user off for it would be noise.
     *
     * @return true if the field should be cleared (accepted, or already present)
     */
    public boolean addTag(String raw)
    {
        if(raw == null || raw.isBlank())
        {
            // The user pressed Enter on an empty field. Not a mistake worth a sentence.
            return false;
        }

        Tag tag;
        try
        {
            tag = Tag.of(raw);
        }
        catch(IllegalArgumentException e)
        {
            // Tag's own messages are already user-readable ("Tag name must be at most 32
            // characters, was 40"); inventing a second wording here would be one more place for
            // the limit to drift.
            tagsError.set(e.getMessage() + ".");
            return false;
        }

        tagsError.set("");

        if(!tags.contains(tag))
        {
            tags.add(tag);
        }

        return true;
    }

    public void removeTag(Tag tag)
    {
        Objects.requireNonNull(tag, "tag");
        tags.remove(tag);
        tagsError.set("");
    }

    // ---- saving --------------------------------------------------------------------

    /**
     * Validates and persists, then reports which of the three things happened.
     *
     * <p>The service does the validating (§7.1) — this method never inspects the title itself. Its
     * job is to build the right command, and to route {@code SaveOutcome} back to the form.
     */
    public SaveResult save()
    {
        clearErrors();
        targetMissing = false;

        SaveOutcome outcome = isEditing()
            ? service.update(new UpdateIdeaCommand(
                editingId, title.get(), descriptionFromForm(), tagSet(), status.get()))
            : service.create(new CreateIdeaCommand(
                title.get(), descriptionFromForm(), tagSet(), status.get()));

        // Exhaustive over the sealed SaveOutcome — no default branch, for the same reason §7.5's
        // BlockRenderer has none.
        return switch(outcome)
        {
            case SaveOutcome.Saved saved ->
            {
                savedIdea = saved.idea();
                yield SaveResult.SAVED;
            }
            case SaveOutcome.Invalid invalid ->
            {
                applyErrors(invalid.validation());
                yield SaveResult.INVALID;
            }
            case SaveOutcome.NotFound notFound ->
            {
                targetMissing = true;
                yield SaveResult.MISSING;
            }
        };
    }

    /**
     * Routes each error to the control it belongs to, by field key.
     *
     * <p>Matching on {@code ValidationError.field()} and never on message text is what lets the
     * wording change without silently unhooking a message from its field.
     */
    private void applyErrors(ValidationResult validation)
    {
        titleError.set(joined(validation, IdeaValidator.FIELD_TITLE));
        descriptionError.set(joined(validation, IdeaValidator.FIELD_DESCRIPTION));
        tagsError.set(joined(validation, IdeaValidator.FIELD_TAGS));
    }

    private static String joined(ValidationResult validation, String field)
    {
        // Empty string when the field is fine — that is what the view's "is it non-empty" binding
        // keys off, so there is no separate "has an error" flag to keep in step.
        return String.join(" ", validation.messagesFor(field));
    }

    private void clearErrors()
    {
        titleError.set("");
        descriptionError.set("");
        tagsError.set("");
    }

    private Set<Tag> tagSet()
    {
        return new LinkedHashSet<>(tags);
    }

    /** Blank text area to an empty description, not a description holding one empty block. */
    private Description descriptionFromForm()
    {
        String text = descriptionText.get();

        if(text == null || text.isBlank())
        {
            return Description.empty();
        }

        return Description.ofText(text.strip());
    }

    /**
     * Flattens a stored description into the one text area Phase 4 has.
     *
     * <p>Text blocks are joined with a blank line; link and image blocks are skipped, because this
     * phase has no way to render or edit them. Nothing in the app can create such a block before
     * Phase 6, so in practice this sees zero or one block. The skip is written down anyway so that
     * when Phase 6 arrives, the lossy path is a known thing being replaced rather than a surprise.
     */
    private static String textOf(Description description)
    {
        StringBuilder joined = new StringBuilder();

        for(ContentBlock block : description.blocks())
        {
            if(block instanceof TextBlock text)
            {
                if(!joined.isEmpty())
                {
                    joined.append(BLOCK_SEPARATOR);
                }

                joined.append(text.text());
            }
        }

        return joined.toString();
    }
}
