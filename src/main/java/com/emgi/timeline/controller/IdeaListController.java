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

    // ---- Phase 5: the filter/sort model. The view binds to these and sets nothing else. ----

    private final StringProperty searchText = new SimpleStringProperty(this, "searchText", "");

    private final ObjectProperty<SortOrder> sortOrder =
        new SimpleObjectProperty<>(this, "sortOrder", SortOrder.NEWEST_FIRST);

    /** Mutable on purpose — the chips own this one. */
    private final ObservableList<Tag> selectedTags = FXCollections.observableArrayList();

    /** Every tag in use across every idea, sorted by name. Derived from {@code master}. */
    private final ObservableList<Tag> availableTags = FXCollections.observableArrayList();

    private final ObservableList<Tag> availableTagsView =
        FXCollections.unmodifiableObservableList(availableTags);

    /** The unfiltered list, so the view can tell "no ideas" from "no matches". */
    private final ObservableList<Idea> allIdeasView =
        FXCollections.unmodifiableObservableList(master);

    private final ReadOnlyBooleanWrapper filterActive =
        new ReadOnlyBooleanWrapper(this, "filterActive", false);

    /**
     * The status dimension has no control in V1 (§6.4) but is carried through the plumbing, so
     * adding one later is a property and one line in {@link #applyQuery()}.
     */
    private Set<IdeaStatus> statuses = Set.of();

    public IdeaListController(IdeaService service)
    {
        this.service = Objects.requireNonNull(service, "service");

        // Each control re-derives the whole query; master drives the chip list.
        //
        // The two ListChangeListener casts are not decoration: ObservableList.addListener is
        // overloaded on InvalidationListener and ListChangeListener, so a bare lambda is
        // ambiguous and will not compile.
        //
        // Ordering note: `filtered` is a field initialized at declaration, so FilteredList
        // subscribed to `master` before this constructor body ran. That is what makes it safe for
        // refreshAvailableTags to re-apply the query from inside a master change notification.
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

    /**
     * Every idea the controller holds, filter or no filter. The view needs this to tell an empty
     * database from a query that hides everything.
     */
    public ObservableList<Idea> allIdeas()
    {
        return allIdeasView;
    }

    public void load()
    {
        master.setAll(service.findAll());
    }

    public IdeaQuery query()
    {
        return query;
    }

    /**
     * Sets every dimension at once. The properties remain the source of truth — this writes into
     * them and lets the listeners do the work.
     *
     * <p>It re-derives the query once per property it changes. That is deliberate: a guard flag to
     * collapse them into one pass would save a comparison over a few hundred rows and buy a
     * re-entrancy bug.
     */
    public void setQuery(IdeaQuery newQuery)
    {
        Objects.requireNonNull(newQuery, "newQuery");

        this.statuses = newQuery.anyOfStatus();

        // An absent term is the empty string here, because that is what an empty search box holds.
        searchText.set(newQuery.titleContains().orElse(""));
        selectedTags.setAll(newQuery.anyOfTags());
        sortOrder.set(newQuery.sortOrder());

        // Covers the case where none of the setters changed anything — setQuery(IdeaQuery.all())
        // on a fresh controller — where no listener would have fired.
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

    /** The live, mutable selection — the chips toggle it directly. */
    public ObservableList<Tag> selectedTags()
    {
        return selectedTags;
    }

    /** Every tag in use, name-sorted. Read-only: only {@code master} decides what is in it. */
    public ObservableList<Tag> availableTags()
    {
        return availableTagsView;
    }

    /**
     * True when a title search or a tag filter is narrowing the list. The sort order is not a
     * filter — it changes how the list reads, not what is in it.
     */
    public ReadOnlyBooleanProperty filterActiveProperty()
    {
        return filterActive.getReadOnlyProperty();
    }

    /** Selects the tag if it isn't selected, deselects it if it is. What a chip click means. */
    public void toggleTag(Tag tag)
    {
        Objects.requireNonNull(tag, "tag");

        // remove returns false when the tag wasn't there, so one call decides both branches.
        if(!selectedTags.remove(tag))
        {
            selectedTags.add(tag);
        }
    }

    /**
     * Drops the search term and every selected tag. <strong>Leaves the sort order alone</strong>:
     * the order is how you read the list, not a claim about what is in it.
     */
    public void clearFilters()
    {
        searchText.set("");
        selectedTags.clear();
    }

    /**
     * Puts a newly created idea into the list.
     *
     * <p>The idea is already persisted by the time it gets here (§7.1) — the editor's save path
     * went through the service. This method only makes it visible, and the filtered/sorted chain
     * decides where it lands.
     */
    public void add(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");
        master.add(idea);
    }

    /**
     * Swaps an edited idea in at the position its predecessor held.
     *
     * <p>{@code set(index, ...)} rather than remove-then-add, per §7.2: a remove clears the
     * ListView's selection and can jump the scroll position, and the user just pressed Save on
     * that exact row.
     *
     * @throws IllegalArgumentException if no idea with that id is in the list — a replace for a
     *         row that is not on screen is a wiring bug, not a user event
     */
    public void replace(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");

        int index = indexOf(idea.id());
        if(index < 0)
        {
            throw new IllegalArgumentException("No idea with id " + idea.id() + " is in the list");
        }

        master.set(index, idea);
    }

    /**
     * Deletes an idea from storage and from the list.
     *
     * <p>Deleting an id that is already gone is a no-op, not an exception (§7.3) — two clicks on
     * a stale row must not crash the app — so the row is dropped either way.
     *
     * @return whether storage actually held it
     */
    public boolean delete(Idea idea)
    {
        Objects.requireNonNull(idea, "idea");

        boolean existed = service.delete(idea.id());

        int index = indexOf(idea.id());
        if(index >= 0)
        {
            master.remove(index);
        }

        return existed;
    }

    /**
     * Position in the master list, by id.
     *
     * <p>By id and not by {@code equals}: after an edit the list holds a different {@code Idea}
     * value with the same identity, and object equality would miss it.
     */
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

    /**
     * Recomputes the chip list from {@code master}, and lifts any filter that no longer refers to
     * a tag in use (§8: "the chip must disappear and the filter must reset").
     *
     * <p>Driven by a listener on {@code master}, so load/add/replace/delete all get this for free
     * and none of them has to know it exists.
     */
    private void refreshAvailableTags()
    {
        SortedSet<Tag> inUse = new TreeSet<>(Comparator.comparing(Tag::name));

        for(Idea idea : master)
        {
            inUse.addAll(idea.tags());
        }

        List<Tag> ordered = new ArrayList<>(inUse);

        // Only when the content really changed: an unconditional setAll fires a change on every
        // reload and rebuilds the chip row for nothing.
        if(!availableTags.equals(ordered))
        {
            availableTags.setAll(ordered);
        }

        // retainAll fires the selectedTags listener, which re-applies the query. Calling
        // applyQuery() here as well would just do the same work twice.
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
