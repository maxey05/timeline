package com.emgi.timeline.controller;

import com.emgi.timeline.domain.command.CreateIdeaCommand;
import com.emgi.timeline.domain.command.UpdateIdeaCommand;
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

public final class IdeaEditorController
{
    public enum SaveResult
    {
        SAVED,

        INVALID,

        MISSING
    }

    private static final String SNAPSHOT_SEPARATOR = "\u001f";

    private final IdeaService service;

    private final StringProperty title = new SimpleStringProperty("");

    private final StringProperty description = new SimpleStringProperty("");

    private final ObjectProperty<IdeaStatus> status =
            new SimpleObjectProperty<>(IdeaStatus.INCOMPLETE);
    private final ObservableList<Tag> tags = FXCollections.observableArrayList();

    private final ReadOnlyStringWrapper titleError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper descriptionError = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper tagsError = new ReadOnlyStringWrapper("");

    private IdeaId editingId;

    private Idea savedIdea;

    private boolean targetMissing;

    private List<String> openingSnapshot = List.of();

    public IdeaEditorController(IdeaService service)
    {
        this.service = Objects.requireNonNull(service, "service");
    }

    public StringProperty titleProperty()
    {
        return title;
    }

    /**
     * The whole description, as one string.
     *
     * <p>The editor binds a single text box to this bidirectionally. There is no draft
     * type and no per-piece state any more: what the user sees in the box is the value.
     */
    public StringProperty descriptionProperty()
    {
        return description;
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

    public boolean isDirty()
    {
        return !currentSnapshot().equals(openingSnapshot);
    }

    private List<String> currentSnapshot()
    {
        List<String> lines = new ArrayList<>();

        lines.add("title" + SNAPSHOT_SEPARATOR + text(title.get()).strip());
        lines.add("status" + SNAPSHOT_SEPARATOR + status.get());
        lines.add("description" + SNAPSHOT_SEPARATOR + text(description.get()).strip());

        List<String> names = new ArrayList<>(tags.size());
        for(Tag tag : tags)
        {
            names.add(tag.name());
        }

        names.sort(Comparator.naturalOrder());

        for(String name : names)
        {
            lines.add("tag" + SNAPSHOT_SEPARATOR + name);
        }

        return List.copyOf(lines);
    }

    private static String text(String value)
    {
        return value == null ? "" : value;
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
        description.set("");
        status.set(IdeaStatus.INCOMPLETE);
        tags.clear();

        clearErrors();

        openingSnapshot = currentSnapshot();
    }

    public void beginEdit(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");

        editingId = idea.id();
        savedIdea = null;
        targetMissing = false;

        title.set(idea.title());
        description.set(idea.description().text());
        status.set(idea.status());

        List<Tag> ordered = new ArrayList<>(idea.tags());
        ordered.sort(Comparator.comparing(Tag::name));
        tags.setAll(ordered);

        clearErrors();

        openingSnapshot = currentSnapshot();
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

    /**
     * Hands the form to the service.
     *
     * <p>The block model needed a pre-flight pass here to turn draft rows into blocks and
     * to report the ones that could not convert. One string needs none of that, so every
     * INVALID now comes from {@code IdeaValidator} by way of the service -- there is
     * exactly one place that decides whether a description is acceptable.
     */
    public SaveResult save()
    {
        clearErrors();
        targetMissing = false;

        Description body = new Description(text(description.get()).strip());

        SaveOutcome outcome = isEditing()
            ? service.update(new UpdateIdeaCommand(
                editingId, title.get(), body, tagSet(), status.get()))
            : service.create(new CreateIdeaCommand(
                title.get(), body, tagSet(), status.get()));

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
}
