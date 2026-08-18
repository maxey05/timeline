package com.emgi.timeline.domain;

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
 * Enforces the one architectural rule that a rushed edit is most likely to break:
 * {@code domain/} depends on no framework — not JavaFX, not JDBC (ARCHITECTURE.md §2, §3).
 *
 * <p>Deliberately a source scan rather than an ArchUnit dependency: the project takes no libraries
 * it doesn't need, and reading import lines is enough to catch the mistake this guards against.
 */
class DomainPurityTest {

    private static final Path DOMAIN_SOURCES = Path.of("src/main/java/com/emgi/timeline/domain");

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "import javafx.",
            "import java.sql.",
            "import javax.sql.",
            "import com.emgi.timeline.repository.",
            "import com.emgi.timeline.service.",
            "import com.emgi.timeline.controller.",
            "import com.emgi.timeline.view.");

    @Test
    @DisplayName("the domain source directory is where this test expects it")
    void domainSourcesAreFound() throws IOException {
        // Without this, a wrong working directory would make the scan below silently pass.
        assertThat(Files.isDirectory(DOMAIN_SOURCES))
                .as("expected domain sources at %s (working dir: %s)",
                        DOMAIN_SOURCES, Path.of("").toAbsolutePath())
                .isTrue();
        assertThat(javaFilesInDomain()).as("domain source files").isNotEmpty();
    }

    @Test
    @DisplayName("no domain class imports a framework or an outer layer")
    void domainImportsNothingForbidden() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path file : javaFilesInDomain()) {
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
                .as("forbidden imports in domain/ — the domain must stay framework-free")
                .isEmpty();
    }

    private static List<Path> javaFilesInDomain() throws IOException {
        try (Stream<Path> files = Files.walk(DOMAIN_SOURCES)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
        }
    }
}
