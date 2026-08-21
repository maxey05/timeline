package com.emgi.timeline.controller;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.model.IdeaStatus;
import com.emgi.timeline.domain.model.Tag;
import com.emgi.timeline.domain.query.IdeaQuery;
import com.emgi.timeline.domain.query.SortOrder;
import com.emgi.timeline.service.IdeaService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class IdeaListController
{
    private final IdeaService service;

    private final ObservableList<Idea> master = FXCollections.observableArrayList();
    private final FilteredList<Idea> filtered = new FilteredList<>(master);
    private final SortedList<Idea> sorted = new SortedList<>(filtered);

    private IdeaQuery query = IdeaQuery.all();

    private final StringProperty searchText = new SimpleStringProperty(this, "searchText", "");

    private final ObjectProperty<SortOrder> sortOrder =
        new SimpleObjectProperty<>(this, "sortOrder", SortOrder.NEWEST_FIRST);

    private final ObservableList<Tag> selectedTags = FXCollections.observableArrayList();

    private final ObservableList<Tag> availableTags = FXCollections.observableArrayList();

    private final ObservableList<Tag> availableTagsView =
        FXCollections.unmodifiableObservableList(availableTags);

    private final ObservableList<Idea> allIdeasView =
        FXCollections.unmodifiableObservableList(master);

    private final ReadOnlyBooleanWrapper filterActive =
        new ReadOnlyBooleanWrapper(this, "filterActive", false);

    private final ObjectProperty<Idea> selectedIdea =
        new SimpleObjectProperty<>(this, "selectedIdea", null);

    private Set<IdeaStatus> statuses = Set.of();

    public IdeaListController(IdeaService service)
    {
        this.service = Objects.requireNonNull(service, "service");

        searchText.addListener((observable, previous, current) -> applyQuery());
        sortOrder.addListener((observable, previous, current) -> applyQuery());
        selectedTags.addListener((ListChangeListener<Tag>) change -> applyQuery());
        master.addListener((ListChangeListener<Idea>) change -> refreshAvailableTags());

        applyQuery();
    }

    public ObservableList<Idea> ideas()
    {
        return sorted;
    }

    public ObservableList<Idea> allIdeas()
    {
        return allIdeasView;
    }

    public void load()
    {
        master.setAll(service.findAll());

        selectedIdea.set(null);
    }

    public IdeaQuery query()
    {
        return query;
    }

    public void setQuery(IdeaQuery newQuery)
    {
        Objects.requireNonNull(newQuery, "newQuery");

        this.statuses = newQuery.anyOfStatus();

        searchText.set(newQuery.titleContains().orElse(""));
        selectedTags.setAll(newQuery.anyOfTags());
        sortOrder.set(newQuery.sortOrder());

        applyQuery();
    }

    public StringProperty searchTextProperty()
    {
        return searchText;
    }

    public ObjectProperty<SortOrder> sortOrderProperty()
    {
        return sortOrder;
    }

    public ObservableList<Tag> selectedTags()
    {
        return selectedTags;
    }

    public ObservableList<Tag> availableTags()
    {
        return availableTagsView;
    }

    public ReadOnlyBooleanProperty filterActiveProperty()
    {
        return filterActive.getReadOnlyProperty();
    }

    public ObjectProperty<Idea> selectedIdeaProperty()
    {
        return selectedIdea;
    }

    public void select(Idea idea)
    {
        selectedIdea.set(idea);
    }

    public void toggleTag(Tag tag)
    {
        Objects.requireNonNull(tag, "tag");

        if(!selectedTags.remove(tag))
        {
            selectedTags.add(tag);
        }
    }

    public void clearFilters()
    {
        searchText.set("");
        selectedTags.clear();
    }

    public void add(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");
        master.add(idea);
    }

    public void replace(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");

        int index = indexOf(idea.id());
        if(index < 0)
        {
            throw new IllegalArgumentException("No idea with id " + idea.id() + " is in the list");
        }

        boolean wasSelected = isSelected(idea.id());

        master.set(index, idea);

        if(wasSelected)
        {
            selectedIdea.set(idea);
        }
    }

    public boolean delete(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");

        boolean existed = service.delete(idea.id());

        if(isSelected(idea.id()))
        {
            selectedIdea.set(null);
        }

        int index = indexOf(idea.id());
        if(index >= 0)
        {
            master.remove(index);
        }

        return existed;
    }

    private boolean isSelected(IdeaId id)
    {
        Idea current = selectedIdea.get();
        return current != null && current.id().equals(id);
    }

    private int indexOf(IdeaId id)
    {
        for(int i = 0; i < master.size(); i++)
        {
            if(master.get(i).id().equals(id))
            {
                return i;
            }
        }

        return -1;
    }

    private void refreshAvailableTags()
    {
        SortedSet<Tag> inUse = new TreeSet<>(Comparator.comparing(Tag::name));

        for(Idea idea : master)
        {
            inUse.addAll(idea.tags());
        }

        List<Tag> ordered = new ArrayList<>(inUse);

        if(!availableTags.equals(ordered))
        {
            availableTags.setAll(ordered);
        }

        selectedTags.retainAll(availableTags);
    }

    private void applyQuery()
    {
        query = new IdeaQuery(
            Optional.ofNullable(searchText.get()),
            Set.copyOf(selectedTags),
            statuses,
            sortOrder.get());

        filtered.setPredicate(query.toPredicate());
        sorted.setComparator(query.sortOrder().comparator());

        filterActive.set(query.titleContains().isPresent()
            || !query.anyOfTags().isEmpty()
            || !query.anyOfStatus().isEmpty());
    }
}
