package com.emgi.timeline.view;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventTarget;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class WindowChrome
{
    public static final String GLYPH_MINIMIZE = "M 0 5 L 10 5";

    public static final String GLYPH_MAXIMIZE = "M 0.5 0.5 L 9.5 0.5 L 9.5 9.5 L 0.5 9.5 Z";

    public static final String GLYPH_RESTORE =
        "M 2.5 0.5 L 9.5 0.5 L 9.5 7.5 M 0.5 2.5 L 7.5 2.5 L 7.5 9.5 L 0.5 9.5 Z";

    public static final String GLYPH_CLOSE = "M 0.5 0.5 L 9.5 9.5 M 9.5 0.5 L 0.5 9.5";

    private static final String CAPTION_STYLE_CLASS = "caption-button";

    private static final double RESIZE_MARGIN = 5;

    private static final int NORTH = 1;
    private static final int SOUTH = 2;
    private static final int EAST = 4;
    private static final int WEST = 8;

    private final Region titleBar;
    private final Button minimizeButton;
    private final Button maximizeButton;
    private final SVGPath maximizeGlyph;
    private final Button closeButton;

    private Stage stage;

    private BooleanSupplier closeInterceptor = () -> false;

    private double dragOffsetX;
    private double dragOffsetY;
    private boolean dragging;

    private boolean resizing;
    private int resizeEdges;
    private double pressScreenX;
    private double pressScreenY;
    private double pressX;
    private double pressY;
    private double pressWidth;
    private double pressHeight;

    public WindowChrome(Region titleBar,
                        Button minimizeButton,
                        Button maximizeButton,
                        SVGPath maximizeGlyph,
                        Button closeButton)
    {
        this.titleBar = Objects.requireNonNull(titleBar, "titleBar");
        this.minimizeButton = Objects.requireNonNull(minimizeButton, "minimizeButton");
        this.maximizeButton = Objects.requireNonNull(maximizeButton, "maximizeButton");
        this.maximizeGlyph = Objects.requireNonNull(maximizeGlyph, "maximizeGlyph");
        this.closeButton = Objects.requireNonNull(closeButton, "closeButton");
    }

    /**
     * Registers a veto for window-close requests. The supplier runs on every close
     * request — title-bar close button, Alt+F4, or anything else that fires
     * WINDOW_CLOSE_REQUEST. Returning true means the supplier has taken
     * responsibility for the request and the window must stay open.
     */
    public void setCloseInterceptor(BooleanSupplier interceptor)
    {
        this.closeInterceptor = interceptor == null ? () -> false : interceptor;
    }

    public void install(Scene scene)
    {
        Objects.requireNonNull(scene, "scene");

        if(scene.getWindow() instanceof Stage window)
        {
            attach(window, scene);
            return;
        }

        scene.windowProperty().addListener(new ChangeListener<Window>()
        {
            @Override
            public void changed(ObservableValue<? extends Window> observable,
                                Window previous,
                                Window current)
            {
                if(current instanceof Stage window)
                {
                    scene.windowProperty().removeListener(this);
                    attach(window, scene);
                }
            }
        });
    }

    private void attach(Stage window, Scene scene)
    {
        this.stage = window;

        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event ->
        {
            if(closeInterceptor.getAsBoolean())
            {
                event.consume();
            }
        });

        minimizeButton.setOnAction(event -> stage.setIconified(true));
        maximizeButton.setOnAction(event -> toggleMaximize());
        closeButton.setOnAction(event -> requestClose());

        maximizeGlyph.contentProperty().bind(Bindings.createStringBinding(
            () -> stage.isMaximized() ? GLYPH_RESTORE : GLYPH_MAXIMIZE,
            stage.maximizedProperty()));

        titleBar.setOnMousePressed(this::onTitleBarPressed);
        titleBar.setOnMouseDragged(this::onTitleBarDragged);
        titleBar.setOnMouseReleased(event -> dragging = false);
        titleBar.setOnMouseClicked(this::onTitleBarClicked);

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, this::onSceneMoved);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, this::onScenePressed);
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, this::onSceneDragged);
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, this::onSceneReleased);
    }

    private void toggleMaximize()
    {
        stage.setMaximized(!stage.isMaximized());
    }

    private void requestClose()
    {
        WindowEvent request = new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST);
        stage.fireEvent(request);

        if(!request.isConsumed())
        {
            stage.close();
        }
    }

    private void onTitleBarPressed(MouseEvent event)
    {
        if(event.getButton() != MouseButton.PRIMARY || isCaptionButton(event.getTarget()))
        {
            dragging = false;
            return;
        }

        dragOffsetX = event.getScreenX() - stage.getX();
        dragOffsetY = event.getScreenY() - stage.getY();
        dragging = true;
    }

    private void onTitleBarDragged(MouseEvent event)
    {
        if(!dragging)
        {
            return;
        }

        if(stage.isMaximized())
        {
            double screenX = event.getScreenX();
            double screenY = event.getScreenY();

            stage.setMaximized(false);

            Platform.runLater(() ->
            {
                dragOffsetX = stage.getWidth() / 2;
                dragOffsetY = titleBar.getHeight() / 2;

                stage.setX(screenX - dragOffsetX);
                stage.setY(screenY - dragOffsetY);
            });

            return;
        }

        stage.setX(event.getScreenX() - dragOffsetX);
        stage.setY(event.getScreenY() - dragOffsetY);
    }

    private void onTitleBarClicked(MouseEvent event)
    {
        if(event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 2)
        {
            return;
        }

        if(isCaptionButton(event.getTarget()))
        {
            return;
        }

        toggleMaximize();
    }

    private void onSceneMoved(MouseEvent event)
    {
        if(resizing)
        {
            return;
        }

        Scene scene = (Scene) event.getSource();

        scene.getRoot().setCursor(
            cursorFor(edgesAt(scene, event.getSceneX(), event.getSceneY())));
    }

    private void onScenePressed(MouseEvent event)
    {
        if(event.getButton() != MouseButton.PRIMARY)
        {
            return;
        }

        Scene scene = (Scene) event.getSource();
        int edges = edgesAt(scene, event.getSceneX(), event.getSceneY());

        if(edges == 0)
        {
            return;
        }

        resizing = true;
        resizeEdges = edges;

        pressScreenX = event.getScreenX();
        pressScreenY = event.getScreenY();
        pressX = stage.getX();
        pressY = stage.getY();
        pressWidth = stage.getWidth();
        pressHeight = stage.getHeight();

        event.consume();
    }

    private void onSceneDragged(MouseEvent event)
    {
        if(!resizing)
        {
            return;
        }

        double dx = event.getScreenX() - pressScreenX;
        double dy = event.getScreenY() - pressScreenY;

        double minWidth = Math.max(stage.getMinWidth(), 1);
        double minHeight = Math.max(stage.getMinHeight(), 1);

        if((resizeEdges & EAST) != 0)
        {
            stage.setWidth(Math.max(minWidth, pressWidth + dx));
        }

        if((resizeEdges & SOUTH) != 0)
        {
            stage.setHeight(Math.max(minHeight, pressHeight + dy));
        }

        if((resizeEdges & WEST) != 0)
        {
            double width = Math.max(minWidth, pressWidth - dx);

            stage.setX(pressX + pressWidth - width);
            stage.setWidth(width);
        }

        if((resizeEdges & NORTH) != 0)
        {
            double height = Math.max(minHeight, pressHeight - dy);

            stage.setY(pressY + pressHeight - height);
            stage.setHeight(height);
        }

        event.consume();
    }

    private void onSceneReleased(MouseEvent event)
    {
        if(!resizing)
        {
            return;
        }

        resizing = false;
        resizeEdges = 0;

        Scene scene = (Scene) event.getSource();
        scene.getRoot().setCursor(Cursor.DEFAULT);

        event.consume();
    }

    private int edgesAt(Scene scene, double x, double y)
    {
        if(stage == null || stage.isMaximized() || stage.isFullScreen() || !stage.isResizable())
        {
            return 0;
        }

        int edges = 0;

        if(y <= RESIZE_MARGIN)
        {
            edges |= NORTH;
        }

        if(y >= scene.getHeight() - RESIZE_MARGIN)
        {
            edges |= SOUTH;
        }

        if(x <= RESIZE_MARGIN)
        {
            edges |= WEST;
        }

        if(x >= scene.getWidth() - RESIZE_MARGIN)
        {
            edges |= EAST;
        }

        return edges;
    }

    private static Cursor cursorFor(int edges)
    {
        return switch(edges)
        {
            case NORTH -> Cursor.N_RESIZE;
            case SOUTH -> Cursor.S_RESIZE;
            case EAST -> Cursor.E_RESIZE;
            case WEST -> Cursor.W_RESIZE;
            case NORTH | EAST -> Cursor.NE_RESIZE;
            case NORTH | WEST -> Cursor.NW_RESIZE;
            case SOUTH | EAST -> Cursor.SE_RESIZE;
            case SOUTH | WEST -> Cursor.SW_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    private static boolean isCaptionButton(EventTarget target)
    {
        Node node = target instanceof Node ? (Node) target : null;

        while(node != null)
        {
            if(node.getStyleClass().contains(CAPTION_STYLE_CLASS))
            {
                return true;
            }

            node = node.getParent();
        }

        return false;
    }
}
