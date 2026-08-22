package com.emgi.timeline;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class BuildSanityTest
{
    @Test
    void runsOnJava21OrLater()
    {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }

    @Test
    void startUpResourcesAreOnTheClasspath()
    {
        assertThat(getClass().getResource("/com/emgi/timeline/fxml/MainView.fxml")).as("MainView.fxml").isNotNull();
        assertThat(getClass().getResource("/com/emgi/timeline/css/base.css")).as("base.css").isNotNull();
        assertThat(getClass().getResource("/com/emgi/timeline/css/theme-mono.css")).as("theme-mono.css").isNotNull();
    }

    @Test
    void bundledFontFacesAreOnTheClasspath()
    {
        assertThat(getClass().getResource("/com/emgi/timeline/fonts/Arimo-Regular.ttf")).as("Arimo-Regular.ttf").isNotNull();
        assertThat(getClass().getResource("/com/emgi/timeline/fonts/Arimo-Bold.ttf")).as("Arimo-Bold.ttf").isNotNull();
        assertThat(getClass().getResource("/com/emgi/timeline/fonts/Arimo-Italic.ttf")).as("Arimo-Italic.ttf").isNotNull();
        assertThat(getClass().getResource("/com/emgi/timeline/fonts/Arimo-BoldItalic.ttf")).as("Arimo-BoldItalic.ttf").isNotNull();
    }
}
