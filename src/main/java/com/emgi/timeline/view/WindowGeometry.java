package com.emgi.timeline.view;

import com.emgi.timeline.settings.WindowState;
import com.emgi.timeline.settings.WindowStateStore;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;

public final class WindowGeometry
{
    private final WindowStateStore store;

    private double restoredX = Double.NaN;
    private double restoredY = Double.NaN;
    private double restoredWidth = Double.NaN;
    private double restoredHeight = Double.NaN;

    public WindowGeometry(WindowStateStore store)
    {
        this.store = Objects.requireNonNull(store, "store");
    }

    public void install(Stage stage)
    {
        Objects.requireNonNull(stage, "stage");

        restore(stage);
        track(stage);
    }

    private void restore(Stage stage)
    {
        Optional<WindowState> saved = store.load();

        if(saved.isEmpty() || !isReachable(saved.get()))
        {
            return;
        }

        WindowState state = saved.get();

        stage.setX(state.x());
        stage.setY(state.y());
        stage.setWidth(Math.max(state.width(), stage.getMinWidth()));
        stage.setHeight(Math.max(state.height(), stage.getMinHeight()));
        stage.setMaximized(state.maximized());
    }

    private static boolean isReachable(WindowState state)
    {
        for(Screen screen : Screen.getScreens())
        {
            Rectangle2D bounds = screen.getVisualBounds();

            if(state.intersects(bounds.getMinX(), bounds.getMinY(),
                                bounds.getWidth(), bounds.getHeight()))
            {
                return true;
            }
        }

        return false;
    }

    private void track(Stage stage)
    {
        ChangeListener<Number> onBounds = (observable, previous, current) -> capture(stage);

        stage.xProperty().addListener(onBounds);
        stage.yProperty().addListener(onBounds);
        stage.widthProperty().addListener(onBounds);
        stage.heightProperty().addListener(onBounds);

        stage.setOnHiding(event -> store.save(current(stage)));
    }

    private void capture(Stage stage)
    {
        if(stage.isMaximized() || stage.isIconified())
        {
            return;
        }

        restoredX = stage.getX();
        restoredY = stage.getY();
        restoredWidth = stage.getWidth();
        restoredHeight = stage.getHeight();
    }

    private WindowState current(Stage stage)
    {
        return new WindowState(
            restoredX, restoredY, restoredWidth, restoredHeight, stage.isMaximized());
    }
}
