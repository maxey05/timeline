package com.emgi.timeline.controller;

import com.emgi.timeline.domain.model.Idea;
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

    private void applyQuery()
    {
        filtered.setPredicate(query.toPredicate());
        sorted.setComparator(query.sortOrder().comparator());
    }
}
