package com.emgi.timeline.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The counterpart to {@code DomainPurityTest}, guarding the rule that keeps controllers testable
 * without booting JavaFX: {@code controller/} may use {@code javafx.beans} and
 * {@code javafx.collections}, but never the scene graph (ARCHITECTURE.md §2, §5).
 *
 * <p>A controller that imports {@code javafx.scene} has become a view, and every test of it now
 * needs a running toolkit — which is exactly the outcome the layer split exists to prevent.
 */
class ControllerPurityTest {

    private static final Path CONTROLLER_SOURCES =
            Path.of("src/main/java/com/emgi/timeline/controller");

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import javafx.scene.",
            "import javafx.stage.",
            "import javafx.application.",
            "import javafx.fxml.",
            "import java.sql.",
            "import com.emgi.timeline.view.");

    @Test
    @DisplayName("the controller source directory is where this test expects it")
    void controllerSourcesAreFound() throws IOException {
        // Without this, a wrong working directory would make the scan below silently pass.
        assertThat(Files.isDirectory(CONTROLLER_SOURCES))
                .as("expected controller sources at %s (working dir: %s)",
                        CONTROLLER_SOURCES, Path.of("").toAbsolutePath())
                .isTrue();
        assertThat(javaFilesInControllers()).as("controller source files").isNotEmpty();
    }

    @Test
    @DisplayName("no controller class imports the scene graph or the view layer")
    void controllersImportNothingForbidden() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFilesInControllers()) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                for (String forbidden : FORBIDDEN_IMPORT_PREFIXES) {
                    if (line.startsWith(forbidden)) {
                        violations.add(file + ":" + (i + 1) + " → " + line);
                    }
                }
            }
        }

        assertThat(violations)
                .as("forbidden imports in controller/ — controllers must stay toolkit-free")
                .isEmpty();
    }

    private static List<Path> javaFilesInControllers() throws IOException {
        try (Stream<Path> files = Files.walk(CONTROLLER_SOURCES)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }
}
