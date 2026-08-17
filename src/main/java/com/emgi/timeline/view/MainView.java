package com.emgi.timeline.view;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class MainView 
{
    @FXML
    private Label placeholderLabel;

    @FXML
    private void initialize()
    {
        if(placeholderLabel == null)
        {
            throw new IllegalStateException(
                "FXML injection failed, check fx:id and the fx:controller class name."
            );
        }
    }
}
