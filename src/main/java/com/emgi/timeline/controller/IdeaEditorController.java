package com.emgi.timeline.controller;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
import com.emgi.timeline.domain.content.ContentBlock;
import com.emgi.timeline.domain.content.ImageBlock;
import com.emgi.timeline.domain.content.LinkBlock;
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class IdeaEditorController
{
    public enum SaveResult
    {
        SAVED,

        INVALID,

        MISSING
    }

    private final IdeaService service;

    private final StringProperty title = new SimpleStringProperty("");

    private final ObservableList<BlockDraft> blocks = FXCollections.observableArrayList();

    private final ObjectProperty<IdeaStatus> status =
            new SimpleObjectProperty<>(IdeaStatus.INCOMPLETE);
    private final ObservableList<Tag> tags = FXCollections.observableArrayList();

    private final ReadOnlyStringWrapper titleError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper descriptionError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper tagsError = new ReadOnlyStringWrapper("");

    private IdeaId editingId;

    private Idea savedIdea;

    private boolean targetMissing;

    public IdeaEditorController(IdeaService service)
    {
        this.service = Objects.requireNonNull(service, "service");
    }

    public StringProperty titleProperty()
    {
        return title;
    }

    public ObservableList<BlockDraft> blocks()
    {
        return blocks;
    }

    public BlockDraft addBlock(BlockKind kind)
    {
        Objects.requireNonNull(kind, "kind");

        BlockDraft draft = BlockDraft.ofKind(kind);
        blocks.add(draft);
        return draft;
    }

    public void removeBlock(BlockDraft draft)
    {
        Objects.requireNonNull(draft, "draft");

        int index = indexOfBlock(draft);
        if(index >= 0)
        {
            blocks.remove(index);
        }
    }

    public void moveBlockUp(BlockDraft draft)
    {
        Objects.requireNonNull(draft, "draft");

        int index = indexOfBlock(draft);
        if(index <= 0)
        {
            return;
        }

        swapBlocks(index, index - 1);
    }

    public void moveBlockDown(BlockDraft draft)
    {
        Objects.requireNonNull(draft, "draft");

        int index = indexOfBlock(draft);
        if(index < 0 || index >= blocks.size() - 1)
        {
            return;
        }

        swapBlocks(index, index + 1);
    }

    private int indexOfBlock(BlockDraft draft)
    {
        for(int i = 0; i < blocks.size(); i++)
        {
            if(blocks.get(i) == draft)
            {
                return i;
            }
        }

        return -1;
    }

    private void swapBlocks(int from, int to)
    {
        BlockDraft moving = blocks.get(from);
        BlockDraft displaced = blocks.get(to);
        blocks.set(to, moving);
        blocks.set(from, displaced);
    }

    public ObjectProperty<IdeaStatus> statusProperty()
    {
        return status;
    }

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

    public Optional<Idea> savedIdea()
    {
        return Optional.ofNullable(savedIdea);
    }

    public boolean targetMissing()
    {
        return targetMissing;
    }

    public void beginCreate()
    {
        editingId = null;
        savedIdea = null;
        targetMissing = false;

        title.set("");
        blocks.setAll(BlockDraft.ofKind(BlockKind.TEXT));
        status.set(IdeaStatus.INCOMPLETE);
        tags.clear();

        clearErrors();
    }

    public void beginEdit(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");

        editingId = idea.id();
        savedIdea = null;
        targetMissing = false;

        title.set(idea.title());
        blocks.setAll(draftsFor(idea.description()));
        status.set(idea.status());

        List<Tag> ordered = new ArrayList<>(idea.tags());
        ordered.sort(Comparator.comparing(Tag::name));
        tags.setAll(ordered);

        clearErrors();
    }

    public boolean addTag(String raw)
    {
        if(raw == null || raw.isBlank())
        {
            return false;
        }

        Tag tag;
        try
        {
            tag = Tag.of(raw);
        }
        catch(IllegalArgumentException e)
        {
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

    public SaveResult save()
    {
        clearErrors();
        targetMissing = false;

        DescriptionAttempt attempt = readDescription();

        if(!attempt.errors().isEmpty())
        {
            descriptionError.set(String.join(" ", attempt.errors()));
            return SaveResult.INVALID;
        }

        SaveOutcome outcome = isEditing()
            ? service.update(new UpdateIdeaCommand(
                editingId, title.get(), attempt.description(), tagSet(), status.get()))
            : service.create(new CreateIdeaCommand(
                title.get(), attempt.description(), tagSet(), status.get()));

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

    private void applyErrors(ValidationResult validation)
    {
        titleError.set(joined(validation, IdeaValidator.FIELD_TITLE));
        descriptionError.set(joined(validation, IdeaValidator.FIELD_DESCRIPTION));
        tagsError.set(joined(validation, IdeaValidator.FIELD_TAGS));
    }

    private static String joined(ValidationResult validation, String field)
    {
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

    private record DescriptionAttempt(Description description, List<String> errors) { }

    private static List<BlockDraft> draftsFor(Description description)
    {
        List<BlockDraft> drafts = new ArrayList<>();

        for(ContentBlock block : description.blocks())
        {
            drafts.add(BlockDraft.from(block));
        }

        if(drafts.isEmpty())
        {
            drafts.add(BlockDraft.ofKind(BlockKind.TEXT));
        }

        return drafts;
    }

    private DescriptionAttempt readDescription()
    {
        blocks.removeIf(BlockDraft::isBlank);

        List<ContentBlock> converted = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for(int i = 0; i < blocks.size(); i++)
        {
            BlockDraft draft = blocks.get(i);
            int position = i + 1;

            if(draft.isMissingUri())
            {
                errors.add("Block " + position + ": " + draft.kind().addressWord()
                    + " address is required.");
                continue;
            }

            switch(draft.kind())
            {
                case TEXT -> converted.add(new TextBlock(draft.textProperty().get().strip()));

                case LINK ->
                {
                    URI target = parse(draft.uriProperty().get(), position, errors);
                    if(target != null)
                    {
                        converted.add(new LinkBlock(target, draft.labelProperty().get()));
                    }
                }

                case IMAGE ->
                {
                    URI source = parse(draft.uriProperty().get(), position, errors);
                    if(source != null)
                    {
                        converted.add(new ImageBlock(source, draft.altTextProperty().get()));
                    }
                }
            }
        }

        return new DescriptionAttempt(new Description(converted), errors);
    }

    private static URI parse(String raw, int position, List<String> errors)
    {
        try
        {
            return new URI(raw.strip());
        }
        catch(URISyntaxException e)
        {
            errors.add("Block " + position + ": '" + raw.strip() + "' is not a valid address.");
            return null;
        }
    }
}
