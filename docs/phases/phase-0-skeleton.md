# Phase 0 — Skeleton

## Objective
Take the repo from docs-only to a buildable, runnable, testable Maven project: `mvn test`
actually executes tests and `mvn javafx:run` opens a styled empty window whose layout came
from FXML and whose colors came from CSS.

## Prerequisites
- [ ] No prior phase. This is the first one.
- [ ] Reread ARCHITECTURE.md **§1** (stack), **§3** (folder structure), **§11 Risk 5**
      (no `module-info.java`, classpath mode).
- [ ] Toolchain present on the machine — verify in Step 1, don't assume:
      JDK **21** (`java -version` reports 21.x, and `JAVA_HOME` points at it), Maven **3.9+**, git.

## In scope
- `git init` + `.gitignore` + `README.md` (the repo is currently not a git repo at all)
- `pom.xml` — Java 21 release, all V1 dependencies, surefire, `javafx-maven-plugin`
- `src/main/java/com/emgi/timeline/Launcher.java` + `App.java`
- `src/main/java/com/emgi/timeline/view/MainView.java` — the `fx:controller` class, placeholder only
- `src/main/resources/com/emgi/timeline/fxml/MainView.fxml` — empty shell, one placeholder label
- `src/main/resources/com/emgi/timeline/css/base.css` + `theme-mono.css` — the §6.2 palette,
  named once, so no phase after this one hardcodes a hex value
- `src/test/java/com/emgi/timeline/BuildSanityTest.java` — two tests that prove the build
  is real (see Step 8 for why zero tests is *not* good enough)

## Explicitly out of scope for this phase
- **Any type in `domain/`.** No `Idea`, no `Tag`, no `IdeaStatus` — that's Phase 1, and the
  temptation to "just add the enum while I'm here" is exactly what the phase order exists to stop.
- **Any use of `sqlite-jdbc`.** It goes in `pom.xml` as a declared dependency; no line of Java
  in this phase imports `java.sql` or opens a connection. `schema.sql`, `SqliteConnectionSource`,
  and `SchemaInitializer` are Phase 2.
- **Any real UI.** No `ListView`, no `IdeaListCell`, no toolbar, no tag chips, no empty state
  (§6.2's "No ideas yet" screen is a Phase 3 deliverable, not a Phase 0 placeholder).
- `module-info.java`, `jlink`, `jpackage`, `maven-shade-plugin` — Phase 8 (§11 Risk 5).
- Wiring `InMemoryIdeaRepository` into `App.java` — not now, not ever.
- FXML for the editor dialog (§6.3) — Phase 4.

## Locked decisions relevant to this phase
- **#1** — package root is `com.emgi.timeline`. Every directory below `src/main/java` mirrors it,
  and so does every resource path under `src/main/resources` (§3).
- **#8** — SQLite is the app's real store, so `sqlite-jdbc` is a V1 dependency and belongs in the
  pom from the start. Resolving it now means Phase 2 isn't the first time you find out whether the
  native driver extracts correctly on this machine.
- **§11 Risk 5** — no `module-info.java`. V1 is a plain classpath app. This has one immediate
  consequence in Phase 0, covered in Step 5: a main class that `extends Application` will not
  start from a classpath launch, which is why `Launcher` exists.

---

## Task checklist — ordered execution steps

Run every command from the repo root (`C:\emgiStuff\codingProjects\timeline`). PowerShell forms
are given since that's the shell on this machine. Suggested commit granularity: one commit per step
from Step 2 onward.

### Step 1 — verify the toolchain before writing anything

```powershell
java -version          # want: openjdk version "21.x"
javac -version         # want: javac 21.x  — if this differs from java, JAVA_HOME is wrong
mvn -v                 # want: Apache Maven 3.9+, and "Java version: 21"
git --version
echo $env:JAVA_HOME
```

The line that matters is Maven's own `Java version:` — Maven reports the JDK *it* is running on,
and that is the one `maven.compiler.release=21` will be checked against. If `java -version` says 21
but `mvn -v` says 17, fix `JAVA_HOME` now; discovering it after writing the pom turns a
configuration problem into a debugging session.

**Gate:** all four commands succeed and Maven reports Java 21.

### Step 2 — initialize the repo

```powershell
git init
git add docs
git commit -m "docs: architecture blueprint and phase framework"
```

Then create `.gitignore` at the repo root:

```gitignore
# Build output
target/
*.class

# IDE
.idea/
*.iml
.vscode/
.settings/
.project
.classpath

# OS
Thumbs.db
.DS_Store

# Local databases — the real one lives in ~/.timeline, but temp/test dbs must never be committed
*.db
*.db-journal
```

```powershell
git add .gitignore
git commit -m "chore: gitignore"
```

**Gate:** `git status` is clean.

### Step 3 — `pom.xml`

Create `pom.xml` at the repo root.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.emgi</groupId>
  <artifactId>timeline</artifactId>
  <version>1.0-SNAPSHOT</version>
  <name>Timeline</name>
  <description>Personal idea-management desktop app</description>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <javafx.version>21.0.7</javafx.version>
    <junit.version>5.12.2</junit.version>
    <assertj.version>3.27.3</assertj.version>
    <sqlite.version>3.49.1.0</sqlite.version>

    <!-- Launcher, not App — see Step 5. -->
    <main.class>com.emgi.timeline.Launcher</main.class>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.junit</groupId>
        <artifactId>junit-bom</artifactId>
        <version>${junit.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- JavaFX. Maven resolves the OS-specific classifier automatically; no os-maven-plugin needed. -->
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-controls</artifactId>
      <version>${javafx.version}</version>
    </dependency>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-fxml</artifactId>
      <version>${javafx.version}</version>
    </dependency>

    <!-- Declared now (decision #8); first used in Phase 2. -->
    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>${sqlite.version}</version>
    </dependency>

    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <version>${assertj.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.14.0</version>
      </plugin>

      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.6</version>
      </plugin>

      <plugin>
        <groupId>org.openjfx</groupId>
        <artifactId>javafx-maven-plugin</artifactId>
        <version>0.0.8</version>
        <configuration>
          <mainClass>${main.class}</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

Notes on the choices, since a pom is the kind of file that gets copied without being read:

- **Versions.** These were the newest stable releases at the time of writing. If Maven can't
  resolve one, bump the property rather than editing the dependency block. Stay on the JavaFX
  **21.x** line specifically — that's the LTS pairing with Java 21 chosen in §1; JavaFX 22+ is
  a different support line.
- **`maven.compiler.release=21`, not `source`/`target`.** `release` also checks that you aren't
  calling APIs newer than 21, which `source`/`target` silently permits.
- **`javafx-fxml` is a separate artifact** from `javafx-controls`. Forgetting it is the usual cause
  of `ClassNotFoundException: javafx.fxml.FXMLLoader` at Phase 3.
- **junit-bom.** Importing the BOM is what lets the `junit-jupiter` dependency below omit its
  version and stay consistent across the jupiter/platform artifacts.
- **No `<resources>` block.** Maven's default already copies `src/main/resources` verbatim into
  `target/classes`. Don't add filtering — it would mangle `${...}` sequences in CSS.

Run `mvn -q dependency:resolve` once here. It should complete with no errors, having downloaded
JavaFX (including the win-x64 classifier jars) and `sqlite-jdbc`.

**Gate:** `mvn -q dependency:resolve` succeeds.

### Step 4 — create the directory skeleton

```powershell
$dirs = @(
  "src\main\java\com\emgi\timeline\view",
  "src\main\resources\com\emgi\timeline\fxml",
  "src\main\resources\com\emgi\timeline\css",
  "src\test\java\com\emgi\timeline"
)
$dirs | ForEach-Object { New-Item -ItemType Directory -Force -Path $_ }
```

Deliberately **not** created yet: `domain/`, `repository/`, `service/`, `controller/`,
`resources/.../db/`, `test/.../support/`. An empty directory is an invitation. Each one appears in
the phase that fills it.

### Step 5 — `Launcher.java` and `App.java`

Two files, and the split is the point.

**`src/main/java/com/emgi/timeline/Launcher.java`**

```java
package com.emgi.timeline;

import javafx.application.Application;

/**
 * Plain, non-Application entry point.
 *
 * <p>The JVM refuses to start a main class that {@code extends Application} unless the JavaFX
 * modules are on the module path — it fails with "Error: JavaFX runtime components are missing".
 * This app runs on the classpath by design (no {@code module-info.java}, ARCHITECTURE.md §11
 * Risk 5), so the entry point must be a class that does <em>not</em> extend {@code Application}
 * and hands off to {@link Application#launch}. Phase 8's jpackage classpath-mode jar is launched
 * exactly this way, so the seam belongs here from the start rather than being retrofitted.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
```

**`src/main/java/com/emgi/timeline/App.java`** — the composition root. In Phase 0 it composes
nothing; it exists so that Phase 2 has an obvious place to assemble the object graph.

```java
package com.emgi.timeline;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

/**
 * JavaFX entry point and composition root. Launched via {@link Launcher}.
 */
public class App extends Application {

    private static final String FXML_MAIN = "/com/emgi/timeline/fxml/MainView.fxml";
    private static final String CSS_BASE  = "/com/emgi/timeline/css/base.css";
    private static final String CSS_THEME = "/com/emgi/timeline/css/theme-mono.css";

    @Override
    public void start(Stage stage) throws IOException {
        // TODO Phase 2 — build the object graph here, in this order, and nowhere else:
        //   SqliteConnectionSource -> SchemaInitializer.run() -> SqliteIdeaRepository
        //   -> UuidIdGenerator + Clock.systemUTC() -> IdeaService -> IdeaListController.
        // Guardrail: InMemoryIdeaRepository is never wired here, not even temporarily.

        FXMLLoader loader = new FXMLLoader(resource(FXML_MAIN));
        Parent root = loader.load();

        // TODO Phase 3 — pass the controller to the view:
        //   MainView view = loader.getController();
        //   view.bind(ideaListController);

        Scene scene = new Scene(root, 900, 640);
        scene.getStylesheets().addAll(
                resource(CSS_BASE).toExternalForm(),
                resource(CSS_THEME).toExternalForm());

        stage.setTitle("Timeline");
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Resolves a classpath resource, failing with a readable message instead of letting a null
     * URL surface later as an opaque NullPointerException inside FXMLLoader or the CSS parser.
     */
    private static URL resource(String path) {
        return Objects.requireNonNull(
                App.class.getResource(path), "Missing classpath resource: " + path);
    }
}
```

Two things worth understanding rather than copying:

- **Stylesheets go on the `Scene`, not on the root node.** A `Scene` stylesheet applies to every
  node in the graph including ones added later; a node stylesheet applies only to that subtree.
  Since the editor dialog (Phase 4) will build its own `Scene`, expect to add these same two lines
  there too — which is the moment to extract them into a small helper, not now.
- **Load order is `base.css` then `theme-mono.css`.** Later stylesheets win on equal specificity,
  so the theme file can override structural defaults. That ordering is what makes "swap one file
  to re-theme" (§10.5) true rather than aspirational.

### Step 6 — `MainView.fxml` and `MainView.java`

**`src/main/resources/com/emgi/timeline/fxml/MainView.fxml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.control.Label?>
<?import javafx.scene.layout.BorderPane?>
<?import javafx.scene.layout.StackPane?>

<!--
  Phase 0 shell. Its only job is to prove that FXML loading, fx:controller instantiation,
  and @FXML injection all work from the classpath.

  TODO Phase 3 — replace the center entirely:
    top    = header (title + "New Idea" button)
    center = ListView<Idea> with IdeaListCell
    plus the toolbar (search field, sort ChoiceBox) and the tag chip FlowPane (§6.2).
-->
<BorderPane xmlns="http://javafx.com/javafx/21"
            xmlns:fx="http://javafx.com/fxml/1"
            fx:controller="com.emgi.timeline.view.MainView"
            styleClass="root-pane">
    <center>
        <StackPane styleClass="placeholder-pane">
            <Label fx:id="placeholderLabel"
                   styleClass="placeholder-label"
                   text="Timeline — Phase 0 skeleton"/>
        </StackPane>
    </center>
</BorderPane>
```

**`src/main/java/com/emgi/timeline/view/MainView.java`**

```java
package com.emgi.timeline.view;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * fx:controller for MainView.fxml — a <em>view</em> class in this project's vocabulary (§2),
 * not an MVC controller. It may hold {@code @FXML} node references and may format values for
 * display. It must never validate, filter, sort, or construct a domain object.
 */
public class MainView {

    @FXML
    private Label placeholderLabel;

    @FXML
    private void initialize() {
        // Phase 0 scaffolding: proves @FXML injection actually ran. Delete this whole method
        // body in Phase 3 and replace it with the ListView binding.
        if (placeholderLabel == null) {
            throw new IllegalStateException(
                    "FXML injection failed: check fx:id and the fx:controller class name");
        }

        // TODO Phase 3 — accept an IdeaListController via a bind(...) method called from App,
        //      set the ListView's items to controller.sortedIdeas(), install IdeaListCell,
        //      and forward gestures (new / edit / delete) to the controller.
    }
}
```

`initialize()` is `private` deliberately — `FXMLLoader` finds it reflectively, and keeping it
private stops anything outside the FXML machinery from calling it.

### Step 7 — the two stylesheets

The split is not cosmetic: **`base.css` contains no color, `theme-mono.css` contains nothing but
color.** Enforcing that now is what makes a future dark theme a one-file addition (§10.5). If you
ever find yourself typing a hex value into `base.css`, that's the rule breaking.

**`src/main/resources/com/emgi/timeline/css/base.css`**

```css
/*
 * base.css — layout, spacing, typography. THEME-AGNOSTIC.
 * No colors in this file, ever. Colors live in theme-mono.css.
 */

.root {
    -fx-font-family: "Segoe UI", "Inter", "Helvetica Neue", sans-serif;
    -fx-font-size: 13px;
}

.root-pane {
    -fx-padding: 0;
}

/* --- Phase 0 scaffolding: delete along with the placeholder in Phase 3 --- */
.placeholder-pane {
    -fx-padding: 32px;
}

.placeholder-label {
    -fx-font-size: 15px;
}
```

**`src/main/resources/com/emgi/timeline/css/theme-mono.css`**

```css
/*
 * theme-mono.css — COLOR ONLY. This file is the theming seam (§10.5): a dark theme is a
 * second file defining the same variables, swapped at the Scene level. Nothing else changes.
 *
 * Palette values are taken from ARCHITECTURE.md §6.2. Define them once here as looked-up
 * colors and reference the names downstream — no phase after this one should contain a hex literal.
 */

.root {
    -text-primary:      #1a1a1a;   /* idea titles */
    -text-secondary:    #6b6b6b;   /* previews, dates, status labels */
    -surface:           #ffffff;   /* window background */
    -surface-hover:     #f7f7f7;   /* row hover */
    -surface-selected:  #f0f0f0;   /* row selection */
    -separator:         #ececec;   /* 1px row separators */

    -fx-background-color: -surface;
    -fx-text-fill: -text-primary;
}

/* --- Phase 0 scaffolding: delete in Phase 3 --- */
.placeholder-label {
    -fx-text-fill: -text-secondary;
}
```

JavaFX CSS is not web CSS. Two differences that will bite otherwise: every property is prefixed
`-fx-`, and an unrecognized property is **silently ignored** with only a warning on stderr — so a
typo produces a window that looks almost right and no error at all. Watch the console on first run.

### Step 8 — the sanity tests

"`mvn test` passes" is a weaker signal than it looks: a build with a misconfigured surefire, or one
where no test class matched the naming pattern, also prints BUILD SUCCESS. Two tests fix that, and
the second one pre-empts the single most common Phase 3 mystery.

**`src/test/java/com/emgi/timeline/BuildSanityTest.java`**

```java
package com.emgi.timeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the build itself is real: tests actually execute, the toolchain is Java 21, and the
 * classpath resources the app loads at startup are packaged where the loader will look for them.
 *
 * <p>Deliberately does not touch JavaFX — nothing here boots the toolkit.
 */
class BuildSanityTest {

    @Test
    void runsOnJava21OrLater() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }

    @Test
    void startupResourcesAreOnTheClasspath() {
        assertThat(getClass().getResource("/com/emgi/timeline/fxml/MainView.fxml"))
                .as("MainView.fxml").isNotNull();
        assertThat(getClass().getResource("/com/emgi/timeline/css/base.css"))
                .as("base.css").isNotNull();
        assertThat(getClass().getResource("/com/emgi/timeline/css/theme-mono.css"))
                .as("theme-mono.css").isNotNull();
    }
}
```

Keep `startupResourcesAreOnTheClasspath` updated as resources are added — `db/schema.sql` joins it
in Phase 2, `IdeaEditorView.fxml` in Phase 4.

### Step 9 — `README.md`

Short and operational. The architecture story is already in `docs/ARCHITECTURE.md`; don't duplicate it.

```markdown
# Timeline

A personal idea-management desktop app — Java 21 + JavaFX 21, SQLite storage.

## Requirements
- JDK 21
- Maven 3.9+

## Run
    mvn javafx:run

## Test
    mvn test

## Where things are
- `docs/ARCHITECTURE.md` — design blueprint and the record of locked decisions
- `docs/PHASE_TEMPLATE.md` — phase framework and the roadmap table
- `docs/phases/` — one filled-in doc per phase
- Data file (created on first run): `~/.timeline/timeline.db`
```

### Step 10 — the verification gate

Run all six. Phase 0 is not done until every one passes.

1. **Tests execute and pass.**
   ```powershell
   mvn clean test
   ```
   Read the surefire summary, don't just look for BUILD SUCCESS. It must say
   `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`. If it says `Tests run: 0`, see Troubleshooting.

2. **The window opens.**
   ```powershell
   mvn javafx:run
   ```
   Expect a 900×640 window titled *Timeline*, white background, one gray centered label.
   Check the console for JavaFX CSS warnings — there should be none.

3. **CSS is demonstrably applied** (this is the part of the done-when most easily faked).
   Comment out the `scene.getStylesheets().addAll(...)` call, re-run, and confirm the label
   changes: default font size and near-black text instead of 15px gray. Uncomment. If nothing
   changed, the stylesheets never loaded and step 2 proved nothing.

4. **No `module-info.java` anywhere** (§11 Risk 5).
   ```powershell
   Get-ChildItem -Recurse -Filter module-info.java
   ```
   Must return nothing.

5. **No phase has leaked forward.**
   ```powershell
   Get-ChildItem -Recurse -Path src -Include *.java |
       Select-String -Pattern "java\.sql|domain\.model|IdeaRepository"
   ```
   Must return nothing. Any hit means Phase 1 or 2 work has crept into Phase 0.

6. **The tree matches §3 as far as it goes.**
   ```powershell
   Get-ChildItem -Recurse -Path src | Select-Object FullName
   ```
   Compare against §3's layout. Only the eight files from Steps 5–8 should exist under `src`.

Then commit:

```powershell
git add .
git commit -m "feat: phase 0 skeleton — maven build, JavaFX shell, CSS split, sanity tests"
```

---

## Troubleshooting — the five failures that actually happen here

| Symptom | Cause | Fix |
|---|---|---|
| `Error: JavaFX runtime components are missing, and are required to run this application` | A main class that extends `Application` was launched from the classpath (typically an IDE "Run App.java") | Run `Launcher`, not `App` — that's what it's for. Or use `mvn javafx:run`. |
| `Tests run: 0` but BUILD SUCCESS | Test class not matching surefire's pattern, or the class/method is `public`-less *and* the JUnit 5 engine isn't on the path | Class name must end in `Test`. Confirm `junit-jupiter` (the aggregator, not just `junit-jupiter-api`) is a test dependency — the aggregator is what drags in the engine. |
| `NullPointerException: Location is not set` from `FXMLLoader` | Resource path wrong | The path is absolute from the classpath root and must mirror `src/main/resources` exactly: `/com/emgi/timeline/fxml/MainView.fxml`. Classpath lookup is case-sensitive even on Windows. Step 8's test catches this before the app does. |
| Window opens but looks unstyled | A `styleClass` typo, or a `-fx-` property JavaFX doesn't recognize | JavaFX silently ignores unknown CSS properties and logs a warning to stderr. Read the console. Verify with the comment-out check in Step 10.3. |
| `release version 21 not supported` | Maven is running on an older JDK than `java -version` reports | Fix `JAVA_HOME` and re-check `mvn -v`. Step 1 exists to catch this. |

---

## Files created / modified
_(fill in as you go — expected list below)_

- [ ] `.gitignore`
- [ ] `README.md`
- [ ] `pom.xml`
- [ ] `src/main/java/com/emgi/timeline/Launcher.java`
- [ ] `src/main/java/com/emgi/timeline/App.java`
- [ ] `src/main/java/com/emgi/timeline/view/MainView.java`
- [ ] `src/main/resources/com/emgi/timeline/fxml/MainView.fxml`
- [ ] `src/main/resources/com/emgi/timeline/css/base.css`
- [ ] `src/main/resources/com/emgi/timeline/css/theme-mono.css`
- [ ] `src/test/java/com/emgi/timeline/BuildSanityTest.java`

## Tests written
- [ ] `BuildSanityTest` — Java 21 toolchain; startup resources present on the classpath
- [ ] Integration/contract tests: n/a this phase
- [ ] Manual smoke check: `mvn javafx:run` opens a styled window; commenting out the stylesheet
      lines visibly changes it (Step 10.3)

## Definition of done
`mvn javafx:run` shows a styled empty window; `mvn test` passes — **and** the surefire summary
reports a non-zero test count, and the styling is demonstrably coming from the stylesheets rather
than from JavaFX defaults.

## Retro (fill in when the phase is closed)
- Estimated: 1–3 h — Actual: ___
- What took longer than expected, and why:
- Any deviation from ARCHITECTURE.md (and whether the blueprint should be updated to match):
  - _Pre-registered:_ **`Launcher.java` is not in §3's file tree.** It's required by the
    classpath-mode decision (§11 Risk 5) and again by Phase 8's jpackage step. If it's kept,
    add it to §3 and mention it in Risk 5.
  - _Pre-registered:_ **`view/MainView.java` and `MainView.fxml` exist as of Phase 0**, whereas
    §9 lists them under Phase 3. They're placeholders here — the call was that proving the
    FXML/controller/injection path is cheaper now than while also debugging real layout.
    No blueprint change needed unless you disagree with the placement.
  - _Pre-registered:_ **`phase-1-domain.md`'s "Step 0"** duplicates this phase. Once Phase 0 is
    closed, reduce that section to a one-line prerequisite pointing here, so there's one source
    of truth for the build setup.
  - _Pre-registered:_ **Dependency versions are pinned in the pom** (JavaFX 21.0.7,
    sqlite-jdbc 3.49.1.0, JUnit 5.12.2, AssertJ 3.27.3). §1 names the stack but no versions —
    worth adding a version table there if you want the doc to be reproducible.
- Anything punted to a later phase that wasn't originally planned that way:
