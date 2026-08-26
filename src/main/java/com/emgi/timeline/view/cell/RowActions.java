package com.emgi.timeline.view.cell;

import com.emgi.timeline.domain.model.IdeaId;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public final class RowActions 
{
    private final ObjectProperty<IdeaId> open = new SimpleObjectProperty<>(this, "open", null);

    public ObjectProperty<IdeaId> openProperty() 
    { 
        return open;
    }

    public IdeaId opened()
    {
        return open.get();
    }

    public boolean isOpen() 
    { 
        return open.get() != null;
    }

    public boolean isOpen(IdeaId id) 
    { 
        return id != null && id.equals(open.get());
    }

    public void open(IdeaId id) 
    { 
        open.set(id);
    }

    public void close() 
    { 
        open.set(null);
    }

    public void toggle(IdeaId id) 
    { 
        if(id == null || isOpen(id))
        {
            close();
            return;
        }

        open.set(id);
    }
}
