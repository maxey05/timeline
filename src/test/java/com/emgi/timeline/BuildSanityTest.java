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
}
