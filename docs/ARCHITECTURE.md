# Timeline — Technical Blueprint (v1)

**Status:** planning document, pre-implementation
**Stack decision date:** 2026-08-16
**Repo folder:** `C:\emgiStuff\codingProjects\timeline`
**Package root:** `com.emgi.timeline`

A personal idea-management desktop app: create, organize, and revisit ideas with tags and rich descriptions.

---

## 0. Decisions locked in

Resolved with the user on 2026-08-16. This section is the record of *why* the doc reads the way it does — refer back here if a later choice seems to contradict the original brief.

| # | Question | Decision | Consequence |
|---|---|---|---|
| 1 | Product/package name | **`timeline`** | Package root is `com.emgi.timeline` throughout. |
| 2 | Is `status` in V1? | **Yes** | `IdeaStatus` is a required field on `Idea` from Phase 1. |
| 3 | What does "sort by tag" mean? | **It doesn't mean sorting at all — it means filtering.** Selecting/searching a tag shows all ideas carrying that tag. | The ambiguous alphabetical "sort by tag" comparator is **dropped**. Sorting is Newest first / Oldest first only; tag lookup is handled entirely by the existing tag-filter feature. |
| 4 | Tag filter combination | **OR** — an idea matches if it has *any* selected tag | `IdeaQuery.toPredicate()` implements OR within `anyOfTags`. |
| 5 | Editor: modal or inline? | **Modal dialog** | `IdeaEditorView` is shown via `Dialog`/`Stage.showAndWait()`. |
| 6 | Media handling | **Reference by URL**, not embedded/copied | `ImageBlock` stores a `URI`; no asset-management subsystem in V1. |
| 7 | Video support | **Dropped.** "Just hyperlinks" — video links are handled by the existing `LinkBlock` type. | `ContentBlock` has three variants, not four: `TextBlock`, `LinkBlock`, `ImageBlock`. No `MediaPlayer`, no video risk. |
| 8 | Persistence | **SQLite from V1**, not deferred | `SqliteIdeaRepository` is the app's real store from Phase 2. `InMemoryIdeaRepository` is kept — not for the app, but as the fast, deterministic implementation used by tests. |

Two follow-on design changes fell out of decision 8 and are worth flagging up front:

- **No JSON dependency needed.** The original plan deferred SQLite and assumed a JSON blob column for the block list "later." Since SQLite is here now, blocks get a proper `idea_block` table instead (see §4.6) — no serialization library required at all, which is *fewer* dependencies than the original plan, not more.
- **No `module-info.java`.** `sqlite-jdbc` isn't a JPMS module, which makes a fully modular JavaFX app (module-info + `jlink`) more friction than it's worth for a solo project. V1 runs as a plain classpath application. See §11, Risk 5.

---

## 1. Recommended technology stack

| Concern | Choice | Why |
|---|---|---|
| Language | **Java 21 (LTS)** | Gives you `record`, `sealed interface`, and exhaustive pattern-matching `switch`. All three do real work in this design — the rich-content model is much weaker without them. |
| UI toolkit | **JavaFX 21.x (LTS line)** | Scene graph + real CSS. The black/white minimal aesthetic and "future theming without major UI changes" are a CSS file swap, not a code change. |
| UI layout | **FXML for screen skeletons + CSS for all styling**; programmatic construction for dynamic/repeating content (list cells, content blocks) | FXML gives you a structural separation the compiler can't give you: markup can't contain business logic because it can't contain logic. Cells are built in code because they're data-driven. |
| Rich content | **Structured block model** (`sealed interface ContentBlock`, 3 variants) rendered to JavaFX nodes | Domain stays free of HTML/JavaFX. See §7.5. |
| Storage | **SQLite** (embedded, single file) via `org.xerial:sqlite-jdbc`, behind an `IdeaRepository` interface. `InMemoryIdeaRepository` also implemented, used only by tests. | Real persistence with zero server to run — appropriate for a single-user desktop tool. The interface is still the seam: tests stay fast and deterministic against the in-memory implementation. |
| Testing | **JUnit 5 (Jupiter)** + **AssertJ**. Repository tests run as a shared, parameterized suite against both `IdeaRepository` implementations. **TestFX** deferred to post-V1. | Domain, repository, service, and controller are all testable with plain JUnit — no toolkit boot required (see §8). |
| Build | **Maven** + `javafx-maven-plugin` (run/debug) | Simplest pom, best IDE support. |
| Packaging | **`jpackage` in classpath mode** (not `jlink` + modules) — Phase 8 | `sqlite-jdbc` isn't modularized, so a fully modular `jlink` pipeline is disproportionate friction for this project. See Risk 5. |

**Dependencies taken in V1:** `sqlite-jdbc` (JDBC driver only — no ORM). **Deliberately not taken:** no Spring, no Guice, no Lombok, no JSON library (the block table design in §4.6 avoids needing one), no event-bus library. Wiring is a short, explicit sequence of constructor calls in `App.java`.

---

## 2. High-level architecture

Five layers, dependencies pointing strictly **downward**. Nothing below the presentation line may `import javafx.scene.*`.

```
┌─────────────────────────────────────────────────────────┐
│  VIEW            FXML + CSS + cell factories             │
│                  IdeaListView, IdeaEditorView             │
│                  renders nodes, fires callbacks           │
└───────────────────────────┬─────────────────────────────┘
                            │ callbacks in / observable state out
┌───────────────────────────▼─────────────────────────────┐
│  CONTROLLER      IdeaListController, IdeaEditorController │
│                  owns ObservableList + current query      │
│                  translates user intent → service calls   │
└───────────────────────────┬─────────────────────────────┘
─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │ ─ ─ ─  no JavaFX below this line
┌───────────────────────────▼─────────────────────────────┐
│  SERVICE         IdeaService                              │
│                  use cases, validation orchestration,     │
│                  ID + timestamp assignment                │
└───────────────────────────┬─────────────────────────────┘
┌───────────────────────────▼─────────────────────────────┐
│  REPOSITORY      IdeaRepository (interface)                │
│                  SqliteIdeaRepository   ← used by the app  │
│                  InMemoryIdeaRepository ← used by tests    │
└───────────────────────────┬─────────────────────────────┘
┌───────────────────────────▼─────────────────────────────┐
│  DOMAIN          Idea, Tag, IdeaStatus, Description,       │
│                  ContentBlock hierarchy (Text/Link/Image), │
│                  IdeaValidator, IdeaQuery, SortOrder        │
│                  pure Java — zero framework imports        │
└─────────────────────────────────────────────────────────┘
```

**The one naming trap in JavaFX MVC.** JavaFX calls the class bound to an FXML file via `fx:controller` a "controller". It is *not* your MVC controller — it's a view backing class full of `@FXML` node references. Conflating them is the single most common way JavaFX projects end up with business logic in the view.

Convention for this project:

- `*View` — the `fx:controller` class. Holds `@FXML` node fields. Contains **no** domain logic. Its only job: bind nodes to observable state, and forward user gestures to a controller.
- `*Controller` — the MVC controller. Holds **zero** `javafx.scene` references. May hold `javafx.beans`/`javafx.collections` types (see §8 for why that's safe).

**Why an extra Service layer under an MVC brief.** Without it, the controller becomes the only home for use-case logic, and use-case logic is the part you most want to test. `IdeaService` is where "create an idea" means *validate → assign ID → stamp timestamps → persist*. That sequence is testable in a few lines of JUnit. It is not premature abstraction; it's one class that exists so that logic isn't stranded in a UI class.

---

## 3. Project / folder structure

```
timeline/
├── pom.xml
├── README.md
├── .gitignore
├── docs/
│   └── ARCHITECTURE.md              ← this file
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/emgi/timeline/
    │   │       ├── App.java                     entry point + composition root
    │   │       │
    │   │       ├── domain/
    │   │       │   ├── model/
    │   │       │   │   ├── Idea.java
    │   │       │   │   ├── IdeaId.java
    │   │       │   │   ├── IdeaStatus.java
    │   │       │   │   ├── Tag.java
    │   │       │   │   └── Description.java
    │   │       │   ├── command/
    │   │       │   │   ├── CreateIdeaCommand.java   form data, no ID/timestamps
    │   │       │   │   └── UpdateIdeaCommand.java
    │   │       │   ├── content/
    │   │       │   │   ├── ContentBlock.java    sealed interface (3 variants)
    │   │       │   │   ├── TextBlock.java
    │   │       │   │   ├── LinkBlock.java
    │   │       │   │   └── ImageBlock.java
    │   │       │   ├── validation/
    │   │       │   │   ├── IdeaValidator.java
    │   │       │   │   ├── ValidationResult.java
    │   │       │   │   └── ValidationError.java
    │   │       │   └── query/
    │   │       │       ├── IdeaQuery.java       filter criteria, framework-free
    │   │       │       └── SortOrder.java       enum + comparator factory
    │   │       │
    │   │       ├── repository/
    │   │       │   ├── IdeaRepository.java          interface — THE persistence seam
    │   │       │   ├── InMemoryIdeaRepository.java  used by tests
    │   │       │   ├── sqlite/
    │   │       │   │   ├── SqliteIdeaRepository.java
    │   │       │   │   ├── SqliteConnectionSource.java   opens/locates the .db file
    │   │       │   │   ├── SchemaInitializer.java        runs CREATE TABLE IF NOT EXISTS
    │   │       │   │   └── IdeaRowMapper.java            idea/idea_tag/idea_block rows ↔ Idea
    │   │       │   └── resources... (see below)
    │   │       │
    │   │       ├── service/
    │   │       │   ├── IdeaService.java
    │   │       │   ├── IdGenerator.java         interface
    │   │       │   └── UuidIdGenerator.java
    │   │       │
    │   │       ├── controller/
    │   │       │   ├── IdeaListController.java
    │   │       │   └── IdeaEditorController.java
    │   │       │
    │   │       └── view/
    │   │           ├── MainView.java            fx:controller for MainView.fxml
    │   │           ├── IdeaEditorView.java
    │   │           ├── cell/
    │   │           │   └── IdeaListCell.java
    │   │           └── content/
    │   │               ├── BlockRenderer.java   ContentBlock → Node
    │   │               └── BlockEditorFactory.java
    │   │
    │   └── resources/com/emgi/timeline/
    │       ├── fxml/
    │       │   ├── MainView.fxml
    │       │   └── IdeaEditorView.fxml
    │       ├── css/
    │       │   ├── base.css         layout, spacing, typography — theme-agnostic
    │       │   └── theme-mono.css   colors only — the future theming seam
    │       └── db/
    │           └── schema.sql       CREATE TABLE statements, run once at startup
    └── test/
        └── java/com/emgi/timeline/
            ├── domain/ ...
            ├── repository/
            │   ├── IdeaRepositoryContractTest.java   abstract/parameterized —
            │   │                                      run against BOTH impls
            │   ├── InMemoryIdeaRepositoryTest.java
            │   └── SqliteIdeaRepositoryTest.java     uses a temp-file or
            │                                          in-memory (":memory:") db
            ├── service/ ...
            ├── controller/ ...
            └── support/
                ├── FixedClock.java
                ├── SequentialIdGenerator.java
                └── IdeaFixtures.java            test data builders
```

**Directories that carry weight:**

- `domain/` — the only package with no outward dependencies. If you ever `import javafx` or `import java.sql` in here, the design has broken.
- `repository/IdeaRepository.java` — one interface, two implementations. Everything about "where do ideas live" funnels through it, and the interface is what makes `SqliteIdeaRepositoryTest` and `InMemoryIdeaRepositoryTest` able to share one contract test.
- `resources/db/schema.sql` — plain SQL, not a migration framework. For a single-developer app with one schema version, "run `CREATE TABLE IF NOT EXISTS` on startup" is the whole migration story. Revisit only if you ever ship a schema change to a version someone else already has data in.
- `resources/css/base.css` vs `theme-mono.css` — split colors from layout on day one. Adding a dark theme later = adding a second color file and swapping a stylesheet URL.
- `test/support/` — `FixedClock` and `SequentialIdGenerator` make every service test deterministic. Build these in Phase 1, not when you first need them.

---

## 4. Data model

### 4.1 `Idea`

```java
// src/main/java/com/emgi/timeline/domain/model/Idea.java
public record Idea(
        IdeaId id,
        String title,             // required, non-blank
        Description description,  // never null; may be Description.empty()
        Set<Tag> tags,            // never null; may be empty
        IdeaStatus status,        // never null
        Instant createdAt,
        Instant updatedAt
) {
    // TODO: compact constructor — null-check every field, defensive-copy tags
    //       into Set.copyOf(...) so the record is genuinely immutable.
    // TODO: withTitle / withDescription / withTags / withStatus / withUpdatedAt
    //       copy-methods, each returning a new Idea. Note that withUpdatedAt takes
    //       an Instant argument — the record must never read the clock itself.
    //       Timestamps are the service's job, because the service owns the Clock.
}
```

**Why an immutable record and not a mutable JavaBean with JavaFX properties?**

The tempting alternative is `class Idea { StringProperty title; ... }`, which binds straight to the UI. Reject it. It drags `javafx.beans` into your domain, makes equality and copying subtle, and means any view can silently mutate shared state. The cost of immutability is that editing produces a new object and you must replace it in the list rather than mutate in place — a handful of extra lines, in exchange for a domain you can reason about and test without JavaFX. Take that trade.

The editor screen does need mutable, bindable state while the user types. That state lives in `IdeaEditorController` as a **form model** (JavaFX properties), and is converted to an immutable `Idea` only on save. Mutability where the user is typing, immutability everywhere else.

### 4.2 `IdeaId`

```java
public record IdeaId(UUID value) {
    public static IdeaId newId() { return new IdeaId(UUID.randomUUID()); }
    // TODO: fromString / toString for the SQLite TEXT primary key round-trip
}
```

A wrapper type rather than a bare `UUID` or `long`. It costs one file and buys you a compiler that will not let you pass a `TagId` where an `IdeaId` belongs. **UUID over auto-increment integer**: an auto-increment ID can only be assigned by the database after an insert, which complicates the "assign ID, then validate/use it before persisting" flow in `IdeaService`, and it makes `InMemoryIdeaRepository` and `SqliteIdeaRepository` behave subtly differently unless you're careful. UUIDs are assigned by the application, identically in both implementations. Stored as `TEXT` in SQLite.

### 4.3 `Tag`

```java
public record Tag(String name) {
    public static Tag of(String raw) {
        // TODO: trim, collapse internal whitespace, lowercase → canonical form.
        // TODO: reject blank; reject length > 32 (pick a limit and enforce it).
    }
}
```

**Tag as a value object, not an entity.** In V1 a tag *is* its name — two ideas tagged `java` share an equal `Tag`, and `Set<Tag>` deduplication is free. Normalizing in the factory means `"Java"`, `" java "`, and `"java"` collapse to one tag, which is what makes tag lookup (decision #3) actually work as expected.

The known limitation: you cannot rename a tag globally, or give it a color, because there's no single record of it. When you need that, promote `Tag` to an entity with its own `TagId` and a `tag` table. That migration is contained because tag filtering already goes through `IdeaQuery` rather than through raw string comparison scattered in the UI.

`IdeaStatus` is a plain enum: `INCOMPLETE, IN_PROGRESS, COMPLETED`. Give it a `displayName()` so the view never hardcodes user-facing strings.

### 4.4 Rich content

```java
// domain/content/ContentBlock.java
public sealed interface ContentBlock
        permits TextBlock, LinkBlock, ImageBlock { }

public record TextBlock(String text)                      implements ContentBlock { }
public record LinkBlock(URI target, String label)         implements ContentBlock { }
public record ImageBlock(URI source, String altText)      implements ContentBlock { }

// domain/model/Description.java
public record Description(List<ContentBlock> blocks) {
    public static Description empty() { return new Description(List.of()); }
    public static Description ofText(String text) { /* TODO single TextBlock */ }
    public String plainTextPreview(int maxChars) { /* TODO for list rows + search */ }
}
```

A description is an **ordered list of typed blocks**. This is the same shape Notion and modern editors use, and it's the reason the whole design holds together:

- **The domain never knows what a block looks like.** No HTML, no font names, no pixels.
- **`sealed` + exhaustive `switch` makes extension safe.** Adding a fourth block type later means adding one record and letting the compiler point at every `switch` that must now handle it. With a non-sealed hierarchy or a `type` string field, you'd find those places at runtime instead.
- **Video was dropped (decision #7), and nothing was lost.** A video link is just a `LinkBlock` whose `target` happens to point at a video. The block model already covered "just hyperlinks" — no new type was needed to satisfy that requirement, only the removal of the one type that *wasn't* needed.

### 4.5 Dates

`Instant` (UTC) in the model, formatted to local time only at the view boundary. Never store a preformatted date string. The service obtains timestamps from an injected `java.time.Clock` — this is what lets you write `assertThat(idea.createdAt()).isEqualTo(FIXED_INSTANT)` instead of a flaky "is it roughly now" assertion.

### 4.6 SQLite schema

Because a real block table exists, **no JSON serialization library is needed anywhere in this project.**

```sql
-- resources/com/emgi/timeline/db/schema.sql

CREATE TABLE IF NOT EXISTS idea (
  id           TEXT PRIMARY KEY,
  title        TEXT NOT NULL,
  status       TEXT NOT NULL,
  created_at   TEXT NOT NULL,   -- ISO-8601 instant
  updated_at   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS idea_tag (
  idea_id   TEXT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
  tag_name  TEXT NOT NULL,
  PRIMARY KEY (idea_id, tag_name)
);

CREATE TABLE IF NOT EXISTS idea_block (
  idea_id   TEXT NOT NULL REFERENCES idea(id) ON DELETE CASCADE,
  position  INTEGER NOT NULL,        -- 0-based order within the description
  type      TEXT NOT NULL,           -- 'TEXT' | 'LINK' | 'IMAGE'
  text      TEXT,                    -- TEXT block body
  uri       TEXT,                    -- LINK target / IMAGE source
  label     TEXT,                    -- LINK label
  alt_text  TEXT,                    -- IMAGE alt text
  PRIMARY KEY (idea_id, position)
);
```

`idea_block` is intentionally a sparse table (most columns are `NULL` depending on `type`) rather than three separate block tables — with only three block types and a handful of fields each, three tables would mean three near-identical mapping code paths for no real benefit. If the block type list grows substantially post-V1, revisit.

**Where the database file lives:** `~/.timeline/timeline.db` (i.e. `System.getProperty("user.home") + "/.timeline/timeline.db"`), created on first run if missing. Using the user's home directory rather than a path relative to the installed app matters once you get to Phase 8 packaging — a `jpackage` installer's install directory shouldn't be assumed writable, and a fixed home-directory location survives app upgrades/reinstalls.

**No migration framework.** `SchemaInitializer` runs the three `CREATE TABLE IF NOT EXISTS` statements at startup and stops there. This is correct for a single developer with one schema version in the wild. If you ever ship this to other users and later change the schema, that's the point to introduce a real migration tool (e.g. Flyway) — don't add one preemptively.

### 4.7 `IdeaRowMapper` responsibilities

One class, three jobs, all mechanical:

1. `Idea` → `(idea row, N idea_tag rows, M idea_block rows)` for `save()`.
2. `(idea row, its tag rows, its block rows)` → `Idea` for `findById`/`findAll`.
3. `ContentBlock` → sparse `idea_block` row and back, keyed on the `type` discriminator — this is the one place a `switch` over `ContentBlock` exists purely for I/O, separate from `BlockRenderer`'s UI-facing `switch`.

Keep this mapping logic entirely inside `repository/sqlite/` — `IdeaService` and everything above it should never see a `ResultSet` or a SQL string.

---

## 5. MVC responsibilities

### Model (domain + repository + service)

| Owns | Does not own |
|---|---|
| Data structures (`Idea`, `Tag`, `Description`, blocks) | Any knowledge that a UI exists |
| Validation rules (`IdeaValidator`) | Date *formatting* (that's presentation) |
| Filter/sort semantics (`IdeaQuery`, `SortOrder`) | Colors, labels, user-facing copy |
| Use cases (`IdeaService`) | Threading concerns of the UI toolkit |
| Storage (`IdeaRepository`, SQL, schema) | |

`IdeaValidator` returns a `ValidationResult` (a list of `ValidationError` with a field key and a message key) rather than throwing. Errors are an expected outcome of a user typing, not an exceptional condition — and a result object lets the editor highlight *all* bad fields at once instead of one per attempt.

### View (`view/`, FXML, CSS)

Renders, and reports gestures. It may:

- bind labels/lists to observable state exposed by its controller,
- call controller methods like `controller.requestDelete(id)`,
- format an `Instant` for display, choose an icon, apply a style class.

It may **not**: validate, sort, filter, construct an `Idea`, touch a repository, or decide what a "valid" title is.

The blunt test: *if you deleted the entire `view/` package and `resources/`, would every business rule still be present and still be tested?* If yes, the separation holds.

### Controller (`controller/`)

- Receives user intent, calls `IdeaService`.
- Owns the presentation state: the master `ObservableList<Idea>`, the current `IdeaQuery`, the current selection, the current validation errors.
- Translates service results into state the view observes.
- Does **not** implement rules — it *invokes* them. A controller method should read as a short sequence of delegations. If a controller contains an `if` about domain meaning (`if (title.isBlank())`), that rule belongs in the validator.

---

## 6. UI / UX structure

### 6.1 Screens

Two, and a dialog. Deliberately not more.

1. **Main window** — the idea list plus its controls. This is the app.
2. **Idea editor** — create/edit. A **modal dialog** (decision #5).
3. **Delete confirmation** — a standard `Alert`.

### 6.2 Main window layout

```
┌──────────────────────────────────────────────────────────────┐
│  Timeline                                     [ + New Idea ] │   header
├──────────────────────────────────────────────────────────────┤
│  [ Search titles…            ]  Sort: [ Newest first ▾ ]     │   toolbar
│  Tags:  ( all )  ( java )  ( school )  ( urgent )            │   filter chips
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   Rewrite the scheduler                          2 days ago  │
│   A cleaner approach to the priority queue…                  │
│   java  school                              ● In progress    │
│  ────────────────────────────────────────────────────────    │
│   Portfolio site ideas                           Aug 3       │
│   Static, no framework. Notes and refs below…                │
│   web                                       ○ Incomplete     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

The **Sort** dropdown now has exactly two options, per decision #3: *Newest first* / *Oldest first*. Looking up ideas by tag is done through the **Tags** chip row, not through sorting — clicking `java` narrows the list to ideas tagged `java`, still ordered by the chosen sort.

Hierarchy is carried by **type weight and spacing, not by color or borders** — which is exactly what makes a monochrome palette work:

- Title: 15–16px, medium weight, near-black (`#1a1a1a`).
- Preview: 13px, regular, mid-gray (`#6b6b6b`), single line, ellipsized. Generated by `Description.plainTextPreview(120)`.
- Tags: 11px, uppercase-ish, light-gray pill with 1px border. Never filled with color.
- Date: 12px, light gray, right-aligned.
- Status: a small glyph + label, gray. Not a colored badge.
- Row padding ~16px vertical; separators are 1px `#ececec`, not boxes around each card.
- Selection/hover: a very light gray fill (`#f7f7f7` / `#f0f0f0`). No shadows, no rounded cards, no gradients.

Empty state matters more than it sounds: a centered line ("No ideas yet") plus the New Idea button. It's the first screen you'll ever see and the easiest to forget to build.

Use a `ListView<Idea>` with a custom `IdeaListCell`. Cell reuse means a large list stays smooth without any work from you.

### 6.3 Editor layout

```
┌────────────────────────────────────────────────────────┐
│  New Idea                                              │
│                                                        │
│  Title                                                  │
│  [                                                  ]  │
│  ⚠ Title is required.                    ← error slot  │
│                                                        │
│  Tags                                                  │
│  [ java ×] [ school ×]  [ add tag…            ]        │
│                                                        │
│  Status   ( ) Incomplete  (•) In progress  ( ) Completed│
│                                                        │
│  Description                                           │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ¶  [ text block — multiline field         ] ⌃ ⌄ ✕ │  │
│  │ 🔗 [ url ] [ label ]                       ⌃ ⌄ ✕ │  │
│  │ 🖼  [ image url ] [ alt text ]              ⌃ ⌄ ✕ │  │
│  └──────────────────────────────────────────────────┘  │
│  [ + Text ]  [ + Link ]  [ + Image ]                   │
│                                                        │
│                            [ Cancel ]  [ Save ]        │
└────────────────────────────────────────────────────────┘
```

**This is the most important scope decision in the project.** The description editor is a **vertical list of per-block editors with add/reorder/remove**, *not* an inline WYSIWYG document where you type and images flow into the text. See §11 for why.

Reserve the error label's vertical space permanently (make it invisible, not un-managed) so the form doesn't jump when validation fires.

### 6.4 Filter and sort controls

- **Search**: a plain `TextField` filtering on title, case-insensitive substring. Debounce ~200ms if the list ever gets large; don't bother in V1.
- **Tags**: a horizontal `FlowPane` of toggle chips built from the union of all tags in use. Multi-select, combined with **OR** (decision #4) — an idea matches if it has *any* selected tag.
- **Sort**: a `ChoiceBox<SortOrder>` — Newest first / Oldest first. (No "by tag" option — see decision #3.)
- **Status**: filtering by status isn't in your V1 list, but `IdeaQuery` should carry the field anyway. Zero cost, and it means adding the control later is a view-only change.

### 6.5 Rendering rich content

`BlockRenderer` pattern-matches a `ContentBlock` into a JavaFX `Node`:

| Block | Node | Notes |
|---|---|---|
| `TextBlock` | `Label` / `Text` with wrapping | `setWrapText(true)` |
| `LinkBlock` | `Hyperlink` | Opens in the system browser via `HostServices` — **do not** open an in-app browser. This is also how a "video link" behaves; there is no special video handling. |
| `ImageBlock` | `ImageView` in a container | Load with `new Image(url, true)` for background loading; cap the display width; show alt text on failure |

Blocks are laid into a `VBox`. Read view and editor view share the same block *list*, differing only in whether `BlockRenderer` or `BlockEditorFactory` produces the nodes.

---

## 7. Data flow for major operations

### 7.1 Create

```
User clicks "New Idea"
  → MainView calls IdeaListController.beginCreate()
  → editor dialog opens with an empty form model

User types, clicks Save
  → IdeaEditorView.onSave() → IdeaEditorController.save()
  → controller builds a CreateIdeaCommand(title, description, tags, status)
      from the form model  [no Idea yet — no ID, no timestamps]
  → IdeaService.create(command)
       ├─ IdeaValidator.validate(command) → ValidationResult
       │     └─ if invalid: return result; controller pushes errors to view; STOP
       ├─ IdeaId id = idGenerator.newId()
       ├─ Instant now = clock.instant()
       ├─ Idea idea = new Idea(id, title, description, tags, status, now, now)
       └─ repository.save(idea)   // SQLite: INSERT into idea/idea_tag/idea_block
                                    // within one transaction
  → controller closes the dialog and notifies IdeaListController
  → IdeaListController adds the idea to the master ObservableList
  → FilteredList/SortedList re-evaluate → ListView repaints automatically
```

Three details worth internalizing:

- **`CreateIdeaCommand` is not an `Idea`.** An `Idea` always has an ID and timestamps; a half-filled form does not. Using a separate command type means `Idea` never exists in an invalid state. Commands live in **`domain/command/`**, not in `service/` — `IdeaValidator` (a domain class) takes a command as input, and the domain must not depend on the service layer.
- **ID and timestamp assignment lives in the service, not the constructor.** That's what makes it injectable and therefore testable.
- **`save()` writes to three tables and must be one transaction.** If the block-row inserts fail after the idea row commits, you get an idea with a corrupted description. `SqliteIdeaRepository.save()` should open a transaction, delete-then-reinsert the idea's tag and block rows, insert/update the idea row, and commit or roll back as a unit.

### 7.2 Edit

```
User selects an idea, clicks Edit
  → controller loads the immutable Idea into a mutable form model
  → user edits; the stored Idea is untouched throughout

Save
  → controller builds UpdateIdeaCommand(id, title, description, tags, status)
  → IdeaService.update(command)
       ├─ repository.findById(id)  → if empty, return NOT_FOUND
       ├─ validate
       ├─ Idea updated = existing
       │        .withTitle(...).withDescription(...).withTags(...).withStatus(...)
       │        .withUpdatedAt(clock.instant())      // createdAt preserved
       └─ repository.save(updated)   // upsert; same transactional delete+reinsert
                                       // of tag/block rows as create
  → controller replaces the item in the ObservableList (set at index, not remove+add,
    so selection and scroll position survive)
```

Cancel is free: the form model is discarded and the stored `Idea` was never mutated. That's the immutability trade paying for itself.

### 7.3 Delete

Confirm dialog → `IdeaService.delete(id)` → `repository.delete(id)` (SQLite: `DELETE FROM idea WHERE id = ?`; `ON DELETE CASCADE` removes the tag/block rows) → remove from `ObservableList`. Deleting a non-existent ID is a **no-op, not an exception** (two clicks on a stale row shouldn't crash the app).

### 7.4 Filter and sort

The rules live in the domain; JavaFX only applies them.

```java
// domain/query/IdeaQuery.java
public record IdeaQuery(
        Optional<String> titleContains,
        Set<Tag> anyOfTags,          // empty = no tag filter
        Set<IdeaStatus> anyOfStatus, // empty = no status filter
        SortOrder sortOrder
) {
    public Predicate<Idea> toPredicate() { /* TODO — AND across dimensions, OR within */ }
    public static IdeaQuery all() { /* TODO default: no filters, NEWEST_FIRST */ }
}

// domain/query/SortOrder.java
public enum SortOrder {
    NEWEST_FIRST, OLDEST_FIRST;
    public Comparator<Idea> comparator() { /* TODO — tiebreak on id so ordering
                                                    never jitters when two ideas
                                                    share a createdAt instant */ }
}
```

Wiring in `IdeaListController`:

```java
ObservableList<Idea> master   = FXCollections.observableArrayList();
FilteredList<Idea>   filtered = new FilteredList<>(master);
SortedList<Idea>     sorted   = new SortedList<>(filtered);
// view binds to `sorted`; on query change:
filtered.setPredicate(query.toPredicate());
sorted.setComparator(query.sortOrder().comparator());
```

At startup, `master` is populated once from `repository.findAll()`. **V1 loads everything into memory and filters/sorts there** — `IdeaQuery` is not pushed down into a SQL `WHERE` clause. For a personal idea list (tens to low thousands of rows), that's genuinely fine and much simpler. If the dataset ever grows large enough that this is noticeably slow, that's the point to add `IdeaRepository.find(IdeaQuery query)` and translate it to SQL — the call sites don't change, because they already pass an `IdeaQuery` object.

`FilteredList`/`SortedList` handle incremental re-evaluation and keep the `ListView` in sync — you never rebuild the list by hand. Every filter/sort rule here is unit-testable on a plain `List<Idea>` with no UI and no database at all.

### 7.5 Rich content storage and rendering

**Storage:** each `ContentBlock` in a `Description` maps to one row in `idea_block` (§4.6), ordered by `position`. Media (`ImageBlock.source`) are **URI references** (decision #6) — nothing is copied or embedded; if the file moves, the reference breaks and the UI shows a placeholder.

**Rendering:**

```java
// view/content/BlockRenderer.java
public Node render(ContentBlock block) {
    return switch (block) {                     // exhaustive: sealed interface
        case TextBlock t  -> /* TODO wrapping Label */;
        case LinkBlock l  -> /* TODO Hyperlink → hostServices.showDocument(...) */;
        case ImageBlock i -> /* TODO ImageView, background load, width cap, alt on error */;
    };
}
```

No `default` branch — that's deliberate. When you add a fourth block type, this method stops compiling and tells you exactly where to look. A `default` clause would silently render nothing instead.

---

## 8. Testing strategy

### Unit tests (the bulk — fast, no toolkit, no database)

| Target | What to assert |
|---|---|
| `Tag.of` | normalization: case, trim, internal whitespace; blank rejected; length limit |
| `IdeaValidator` | blank/whitespace title rejected; max lengths; malformed URIs in blocks; multiple errors returned at once |
| `IdeaQuery.toPredicate` | each dimension alone; AND across dimensions; OR within tags; empty filters match everything |
| `SortOrder.comparator` | both orders; totality (no equal-compare pairs left unordered — tiebreak on id) |
| `IdeaService` (against `InMemoryIdeaRepository`, with `FixedClock` + `SequentialIdGenerator`) | exact `createdAt`; `update` preserves `createdAt` and bumps `updatedAt`; invalid input never reaches the repository (use a spy/fake to prove it) |
| `Description.plainTextPreview` | truncation, ellipsis, non-text blocks skipped, empty description |

### Repository contract test (the payoff of the interface)

Write one shared test class — `IdeaRepositoryContractTest` — expressed only in terms of `IdeaRepository`, and run it against **both** implementations:

- `InMemoryIdeaRepositoryTest extends IdeaRepositoryContractTest` — trivial setup.
- `SqliteIdeaRepositoryTest extends IdeaRepositoryContractTest` — points `SqliteConnectionSource` at SQLite's `:memory:` URL (or a fresh temp file per test) so tests never touch the real `~/.timeline/timeline.db`, stay hermetic, and stay fast.

Cover: save/find/delete/upsert; a description with all three block types survives a round trip in the same order; tags survive a round trip with normalization intact; `findAll` doesn't leak a mutable reference to internal state; deleting an idea removes its tag and block rows (foreign-key cascade actually fires — SQLite requires `PRAGMA foreign_keys = ON` per connection, easy to forget).

### Integration tests

- `IdeaService` + real `InMemoryIdeaRepository`: full create → query → update → delete round-trip.
- `IdeaListController` + real service + `InMemoryIdeaRepository`, asserting on the exposed `ObservableList` — create an idea, assert it appears; change the query, assert the filtered contents.

**This last one is possible without booting JavaFX**, and it's worth knowing why: `ObservableList`, `FilteredList`, `SortedList`, and `Property` live in the **`javafx.base`** module, which has no native code and no toolkit dependency. `javafx.graphics` (`Scene`, `Stage`, `Node`) is what requires `Application.launch()`. Keeping controllers to `javafx.base` types is what makes them plain-JUnit testable — this is the concrete payoff of the "controllers hold no `javafx.scene` references" rule.

### UI tests

Defer TestFX to post-V1. For V1, keep a short manual smoke checklist in `docs/` (create → appears in list; edit → row updates; delete → row disappears; filter → correct subset; **restart the app → the idea is still there**, since persistence is real in V1).

### Edge cases to write down now

- Title that is `"   "` (whitespace) — must be rejected. `isEmpty()` won't catch it; use `isBlank()`.
- Same tag entered twice in different cases → one tag.
- Very long title / very long text block → layout must ellipsize, not stretch the window.
- Non-ASCII input (CJK, emoji) in titles, tags, and search — matching must be case-insensitive in a locale-safe way.
- Search string with regex metacharacters (`.*`, `(`) — you're doing substring matching, so make sure you never compile it as a regex.
- Filter selection referencing a tag that no longer exists on any idea (last idea using it was deleted) → the chip must disappear and the filter must reset, not silently show zero results forever.
- Delete while the editor is open on that same idea.
- Broken image URL / `file://` path that no longer exists → alt text or a placeholder, never a crash or a hang on the UI thread.
- Two ideas created in the same millisecond → sort must still be stable.
- App launched for the first time, `~/.timeline/` doesn't exist yet → directory and schema must be created without error.
- Database file present but from a version with a different schema (won't happen in V1, but write the note: `CREATE TABLE IF NOT EXISTS` silently does nothing useful if columns changed — this is the migration gap called out in §4.6).

---

## 9. Version 1 development roadmap

Ordered so that every phase depends only on phases before it, and so architectural commitments happen before anything depends on them.

| Phase | Deliverable | Done when |
|---|---|---|
| **0 — Skeleton** | Maven project (no `module-info.java` — see §11 Risk 5), JavaFX window opens, `base.css` + `theme-mono.css` loaded and visibly applied, JUnit runs | `mvn javafx:run` shows a styled empty window; `mvn test` passes |
| **1 — Domain** | `Idea`, `IdeaId`, `Tag`, `IdeaStatus`, `Description`, all three `ContentBlock` types, the two commands, `IdeaValidator`, `IdeaQuery`, `SortOrder`. Test fixtures + `FixedClock`. **No UI, no database.** | Full unit-test coverage of every rule in §8; zero framework imports in `domain/` |
| **2 — Storage + service** | `IdeaRepository` interface; `InMemoryIdeaRepository`; `SqliteIdeaRepository` + `SchemaInitializer` + `IdeaRowMapper`; `IdeaRepositoryContractTest` run against both; `IdeaService` | Contract tests green against both implementations; round-trip through real SQLite verified |
| **3 — Read-only list** | `MainView.fxml`, `IdeaListCell`, seeded sample data (via the real SQLite repository), full monochrome styling, empty state | The list looks finished, even though nothing is editable |
| **4 — CRUD** | Editor dialog, create/edit/delete, validation errors in the form. **Description = a single text block only.** | Full CRUD works end to end, including a restart of the app confirming data persisted |
| **5 — Organization** | Tag chips (tag lookup per decision #3), title search, sort control | All filter/sort rules from §7.4 work in the UI |
| **6 — Rich content** | `BlockRenderer` + `BlockEditorFactory`; add/reorder/remove blocks. Ship in order: **text → link → image** | Each block type works before starting the next |
| **7 — Polish** | Keyboard shortcuts (Ctrl+N, Enter to save, Esc to cancel), focus order, window sizing, unsaved-changes prompt | Usable without a mouse |
| **8 — Packaging** | `jpackage` in classpath mode, bundling `sqlite-jdbc`; README, manual smoke checklist | An installer runs on a machine with no JDK, and the packaged app persists data across runs |

**V1 ends at Phase 8.** Phase 6 is the one at real risk of expanding — hold the line at "add/reorder/remove typed blocks", not "a word processor".

**Explicitly NOT in V1:** favorites/pinning, export/import, cloud sync, accounts, dark theme, drag-and-drop reordering, undo/redo, markdown import, full-text search of descriptions, video playback of any kind, schema migrations/versioning beyond "create if missing".

Two phase-ordering choices worth naming. **Phase 1 has no UI and no database at all** — you'll want to skip ahead to a window, and it's the wrong instinct: the domain is what everything else depends on, and it's the only layer that's genuinely cheap to get right early. **Phase 3 is read-only.** Building the list against seeded data (already going through the real `SqliteIdeaRepository`) separates "does it look right" from "does it work right", and you'll iterate on the styling much faster without dialogs in the way.

---

## 10. Extensibility strategy

**Decisions made now that pay off later:**

1. **`IdeaRepository` is an interface with two implementations already.** Adding a third (e.g. a future cloud-sync-backed one) is a known-shaped exercise, not a new pattern.
2. **`IdeaQuery` is an object, not a parameter list.** Pushing filtering down into a SQL `WHERE` clause later touches no call sites — see §7.4.
3. **`Description` is a block list, and it's already backed by a real relational table (§4.6).** This is the difference between a later feature and a later rewrite.
4. **`ContentBlock` is `sealed`.** New block types are compiler-guided.
5. **Colors are isolated in `theme-mono.css`.** Theming is a file swap.
6. **`Clock` and `IdGenerator` are injected.** Determinism in tests, and no hidden statics.
7. **`domain/` imports no framework — not JavaFX, not `java.sql`.** The whole business layer could be reused behind a web UI, a CLI, or a rewritten desktop front-end.

**Why `InMemoryIdeaRepository` still exists even though SQLite is the real store.** Not as a stepping stone to be discarded — as the permanent, fast, deterministic implementation that every non-repository test runs against. `IdeaServiceTest` shouldn't need a temp SQLite file to verify that `create()` rejects a blank title; that's wasted I/O for a question that has nothing to do with persistence. Reach for `SqliteIdeaRepositoryTest` (part of the contract suite) specifically when you're testing persistence behavior itself.

**Adding features without coupling them to the UI.** The pattern is the same every time: new capability goes in as a domain concept plus a service method (and a schema addition if it needs to persist), and only then gets a control.

- *Favorites*: add `boolean favorite` to `Idea` (and an `INTEGER`/boolean column to the `idea` table) and `Optional<Boolean> favorite` to `IdeaQuery`; add `IdeaService.toggleFavorite(id)`. UI = one star button and one filter chip.
- *Export*: a new `IdeaExporter` interface in a `port/` package, implemented per format. The exporter consumes `List<Idea>` and knows nothing about the UI. The UI is a menu item and a `FileChooser`.
- *New sort*: one enum constant plus a comparator. Because the `ChoiceBox` is populated from `SortOrder.values()`, the control updates itself.

That last point generalizes: **populate UI controls from domain enums** rather than hardcoding items in FXML. Then extending the domain extends the UI for free.

---

## 11. Key risks, trade-offs, and decisions needed before you start

All eight original open decisions are now resolved (§0). What remains here are risks worth knowing about going in, not open questions.

### Risk 1 — Rich-content editing is the real scope monster

Everything else here is days of work; a genuine WYSIWYG editor where images flow inline with text is *months*, and it is the classic way a project like this dies. Your options:

- **JavaFX `HTMLEditor`** — built in, but it produces an HTML string, which destroys the block model, and it looks nothing like the minimal aesthetic you want. Rejected.
- **`WebView` + a JS editor (Quill/ProseMirror)** — genuinely capable, but pulls in the `javafx.web` module (large), forces a Java↔JS bridge, and your domain becomes whatever JSON the JS editor emits. Rejected for V1.
- **Block-list editor (recommended, and what's specified)** — a `VBox` of per-block editor rows with add/move-up/move-down/remove. Not WYSIWYG. Ships in days, matches the domain exactly, and is honestly fine for a personal capture tool.

The trade you're making: users type into discrete blocks rather than one flowing document. Accept it for V1; the block model means a fancier editor can be layered on later without touching the domain or the schema.

### Risk 2 — SQLite writes happening on the JavaFX Application Thread

For a single-user, personal-scale dataset, a local SQLite write is on the order of milliseconds — fast enough that calling `repository.save()` directly from the controller (on the FX thread) is fine for V1 and keeps the code simple. But it *is* real disk I/O, unlike the old in-memory-only plan, so it's worth knowing the failure mode: if the list ever grows very large, or the disk is slow (e.g. a network drive), a synchronous save can produce a visible UI freeze.

Mitigation if it ever becomes noticeable: wrap repository calls in a `javafx.concurrent.Task`, run it off the FX thread, and apply the result back via `Platform.runLater`. Don't build this in V1 pre-emptively — it's real complexity (error handling, cancellation, disabling the Save button mid-write) for a problem you likely won't hit at personal-idea-list scale.

### Risk 3 — Reference vs. embed for media (already decided, but the failure mode is worth knowing)

`ImageBlock` stores a `file:///C:/Users/.../photo.png` or `https://…` reference (decision #6), which means the idea silently breaks if the user moves or deletes that file. `BlockRenderer` must render a clear placeholder — not a crash, not a blank space — when the reference doesn't resolve. This was chosen deliberately over copying media into an app-managed folder, which would make ideas self-contained but require you to own an asset store, orphan cleanup, and a per-user data directory — real scope for a feature that wasn't asked for.

### Risk 4 — SQLite file lifecycle

Because persistence is real from V1, a few things now matter that didn't when data vanished on exit: back up before schema changes during development (it's one file — just copy `~/.timeline/timeline.db`), handle a missing `~/.timeline/` directory on first launch (create it, don't error), and treat a locked/corrupt database file as a user-facing error message, not a stack trace on stdout the user never sees.

### Risk 5 — Packaging a non-modular JavaFX + JDBC app

`sqlite-jdbc` isn't a JPMS module, which makes a fully modular pipeline (`module-info.java` + `jlink`) more trouble than it's worth here — you'd be fighting `--add-modules`/automatic-module edge cases for a solo project with no reuse benefit. V1 runs as a plain classpath app instead: no `module-info.java`, dependencies resolved normally by Maven, and `jpackage` invoked in **classpath mode** (`--input`/`--main-jar`/`--main-class`, not `--module`) against a single runnable jar. Budget extra time in Phase 8 regardless — a plugin like `maven-shade-plugin` (to produce that single jar) plus `jpackage`'s platform-specific quirks (an installer built on Windows only produces a Windows installer) are still real friction the first time through, just a different flavor of friction than the modular route.

### Risk 6 — Images on the UI thread

`new Image(url)` loads synchronously and will freeze the window on a slow or large source. Always use the background-loading constructor (`new Image(url, true)`), always cap displayed dimensions, and never load full-resolution originals into list cells.

---

## 12. Recommended implementation order

The first ten concrete steps, in order:

1. `pom.xml` — JDK 21, JavaFX 21.x, JUnit 5, AssertJ, `sqlite-jdbc`, `javafx-maven-plugin`. Confirm `mvn test` and `mvn javafx:run` both work on an empty app. No `module-info.java` (Risk 5).
2. `App.java` + an empty `MainView.fxml` + `base.css`/`theme-mono.css`.
3. `IdeaId`, `Tag`, `IdeaStatus` + their unit tests. Smallest possible pieces first; `Tag.of` normalization is a genuinely good first test to write.
4. `ContentBlock` sealed hierarchy (3 variants) + `Description` + `plainTextPreview` tests.
5. `Idea` record with copy-methods; `CreateIdeaCommand`/`UpdateIdeaCommand`; `IdeaValidator` + `ValidationResult` + tests.
6. `IdeaQuery` + `SortOrder` (2 values) + tests. **The domain is now complete and fully tested with no UI and no database in existence.**
7. `IdeaRepository` interface + `InMemoryIdeaRepository` + `IdeaRepositoryContractTest` written against the interface.
8. `schema.sql` + `SchemaInitializer` + `IdeaRowMapper` + `SqliteIdeaRepository`. Run the same contract suite from step 7 against it.
9. `IdGenerator`, `Clock` wiring, `IdeaService` + tests with `FixedClock`, backed by `InMemoryIdeaRepository`.
10. `IdeaListController` with master/filtered/sorted lists + integration tests — still no `Scene`, no `Stage`. Only after this: `IdeaListCell`, `MainView`, and seeded data through the real SQLite repository.

Then Phases 4–8 in the §9 order.

The through-line: **the entire application is correct and tested — domain first, then persistence, then service — before a single pixel is drawn.** That's not discipline for its own sake — it's the only way the MVC separation you asked for actually survives contact with a UI toolkit and a real database.
