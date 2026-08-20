package com.emgi.timeline.controller;

import com.emgi.timeline.domain.model.Idea;
import com.emgi.timeline.domain.model.IdeaId;
import com.emgi.timeline.domain.query.IdeaQuery;
import com.emgi.timeline.service.IdeaService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.Objects;

public final class IdeaListController
{
    private final IdeaService service;

    private final ObservableList<Idea> master = FXCollections.observableArrayList();
    private final FilteredList<Idea> filtered = new FilteredList<>(master);
    private final SortedList<Idea> sorted = new SortedList<>(filtered);

    private IdeaQuery query = IdeaQuery.all();

    public IdeaListController(IdeaService service)
    {
        this.service = Objects.requireNonNull(service, "service");
        applyQuery();
    }

    public ObservableList<Idea> ideas()
    {
        return sorted;
    }

    public void load()
    {
        master.setAll(service.findAll());
    }

    public IdeaQuery query()
    {
        return query;
    }

    public void setQuery(IdeaQuery newQuery)
    {
        this.query = Objects.requireNonNull(newQuery, "newQuery");
        applyQuery();
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

    private void applyQuery()
    {
        filtered.setPredicate(query.toPredicate());
        sorted.setComparator(query.sortOrder().comparator());
    }
}
