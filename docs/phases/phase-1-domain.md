# Phase 1 — Domain

## Objective
Build the complete, framework-free business model for Timeline — types, rules, validation,
and query/sort semantics — with unit tests covering every rule in ARCHITECTURE.md §8, and no
UI and no database anywhere in the phase.

## Prerequisites
- [x] **Phase 0 is essentially done** — verified against the working tree on 2026-08-17.
      See `docs/phases/phase-0-skeleton.md` for the phase itself and "Step 0" below for the
      four small carry-over items that should be closed before Phase 1 adds ~20 new files.
- [ ] Reread ARCHITECTURE.md §4 (data model), §7.4 (filter/sort), §7.5 (rich content), §8 (testing).

## In scope
- `domain/model/`: `Idea`, `IdeaId`, `IdeaStatus`, `Tag`, `Description`
- `domain/content/`: `ContentBlock` (sealed) + `TextBlock`, `LinkBlock`, `ImageBlock`
- `domain/command/`: `CreateIdeaCommand`, `UpdateIdeaCommand`
- `domain/validation/`: `IdeaValidator`, `ValidationResult`, `ValidationError`
- `domain/query/`: `IdeaQuery`, `SortOrder`
- `src/test/.../support/`: `FixedClock`, `SequentialIdGenerator`, `IdeaFixtures`
- Unit tests for every row of the §8 unit-test table that is reachable without a repository
- An architecture guard test that fails if `domain/` ever imports `javafx.*` or `java.sql.*`

## Explicitly out of scope for this phase
- Anything in `repository/`, `service/`, `controller/`, `view/` (Phases 2–4)
- `IdeaService` itself — so the §8 row "`IdeaService` against `InMemoryIdeaRepository`" is
  **Phase 2's** test, not this phase's
- `schema.sql`, SQLite, any FXML/CSS
- Persistence concerns leaking into the model (no `@Column`-ish thinking, no `java.sql`)
- Pushing filtering into SQL — `IdeaQuery.toPredicate()` is an in-memory `Predicate` in V1 (§7.4)

## Locked decisions relevant to this phase
- **#2** — `status` (INCOMPLETE / IN_PROGRESS / COMPLETED) is in V1, so `IdeaStatus` and the
  status dimension of `IdeaQuery` are built now.
- **#3 / #4** — sort is only Newest/Oldest first; tags are a *filter*, combined with **OR**
  within the tag dimension and **AND** across dimensions.
- **#7** — `ContentBlock` has exactly 3 variants. No video type.
- **§4.1** — `Idea` is an immutable `record`. It never reads the clock; `withUpdatedAt(Instant)`
  takes the timestamp as an argument because the service owns the `Clock`.

---

## Verified starting state (checked 2026-08-17)

What actually exists, and what it means for this phase:

| Phase 0 gate | State | Consequence for Phase 1 |
|---|---|---|
| `pom.xml` — Java 21, JavaFX 21.0.7, sqlite-jdbc 3.49.1.0, junit-bom 5.12.2, assertj 3.27.3, surefire 3.5.6, javafx-maven-plugin | Present and correct | **No pom changes needed this phase.** `junit-jupiter` (the aggregator, not just `-api`) and `assertj-core` are already test-scoped, so every test below compiles as-is. |
| `Launcher` + `App` + `view/MainView` + `MainView.fxml` + `base.css` + `theme-mono.css` | All present | Untouched by Phase 1. Do not edit them. |
| `BuildSanityTest` — 2 tests | Green. `target/surefire-reports/` reports `Tests run: 2, Failures: 0, Errors: 0` | The build is real; a new failing test will be visible. |
| No `module-info.java` | Confirmed absent | §11 Risk 5 holds. |
| Nothing leaked forward (no `java.sql`, no `domain.*`) | Confirmed — only 4 `.java` files exist, none import either | `domain/` starts genuinely empty, which is what makes the purity test in Step 9 meaningful from day one. |
| `git init` + commit | Done — 2 commits on `main`, pushed to `origin/main` | — |

**Not verifiable from a headless session — confirm these yourself before starting:**
`mvn javafx:run` opens the styled 900×640 window (Phase 0 gate 10.2), and the comment-out
check proves the styling comes from the stylesheets rather than JavaFX defaults (gate 10.3).

---

## Step 0 — four carry-overs from Phase 0 (~10 minutes, do them first)

None of these block compilation. All four get worse if you start Phase 1 first, because this
phase roughly triples the number of files in the repo.

1. **`README.md` is 0 bytes.** Step 9 of the Phase 0 doc has the exact content — paste it in.
2. **`.gitignore` is missing `*.db`.** It currently has only `*.db-journal`. Phase 2 creates
   temp SQLite files for `SqliteIdeaRepositoryTest`; add the line now, while it's a one-word
   edit rather than an "why is a 40 KB binary in my diff" moment.
3. **The working tree is dirty with pure line-ending churn.** `git status` shows all 9 source
   files modified, but `git diff --ignore-all-space` is *empty* — every change is CRLF↔LF, not
   content. Fix it before adding 20 files:
   ```powershell
   # .gitattributes at the repo root
   * text=auto eol=lf
   ```
   ```powershell
   git add .gitattributes
   git add --renormalize .
   git commit -m "chore: normalize line endings"
   ```
   Left alone, every Phase 1 commit will show hundreds of phantom changed lines and `git diff`
   stops being usable for review.
4. **Close out `phase-0-skeleton.md`.** Its checklists and Retro are still blank. Fill in the
   actual hours and tick the file list — the retro is only useful if it's written while you
   still remember what happened. Its retro also pre-registered two blueprint questions worth
   answering now: whether `Launcher.java` gets added to ARCHITECTURE.md §3, and whether §9
   should acknowledge that `MainView` landed in Phase 0 rather than Phase 3.

---

## Task checklist — ordered execution steps

Work in the order given. Each step ends with `mvn test` green before you start the next one;
that is what keeps a compile error in step 6 from being tangled up with a design mistake in
step 2. Suggested commit granularity: one commit per step.

### Step 1 — value types with no dependencies

Create, in this order (each depends only on what precedes it):

1. `domain/model/IdeaId.java`
   ```java
   package com.emgi.timeline.domain.model;

   public record IdeaId(UUID value) {
       // TODO: compact constructor — reject null value.
       public static IdeaId newId() { return new IdeaId(UUID.randomUUID()); }
       // TODO: static fromString(String) — wrap UUID.fromString; decide and document
       //       whether a malformed string throws IllegalArgumentException (it should).
       // TODO: override toString() to return value.toString() — this is the exact
       //       representation the SQLite TEXT primary key round-trips through in Phase 2.
   }
   ```

2. `domain/model/IdeaStatus.java` — plain enum `INCOMPLETE, IN_PROGRESS, COMPLETED` with a
   `displayName()` field ("Incomplete", "In progress", "Completed"). The view must never
   hardcode these strings, and Phase 5 populates its control from `values()`.

3. `domain/model/Tag.java`
   ```java
   public record Tag(String name) {
       public static final int MAX_LENGTH = 32;

       // TODO: compact constructor — reject null.
       public static Tag of(String raw) {
           // TODO: null-check, trim, collapse runs of internal whitespace to a single
           //       space, lowercase with Locale.ROOT (NOT the default locale — the
           //       Turkish dotless-i problem is real and would make "I" normalize
           //       inconsistently across machines).
           // TODO: reject blank after trimming; reject length > MAX_LENGTH.
           //       Throw IllegalArgumentException — Tag.of is a factory for
           //       already-trusted input; user-facing tag input is checked by
           //       IdeaValidator before it reaches here.
       }
   }
   ```
   **Tests (`TagTest`)**: `"Java"`, `" java "`, `"Ja  va"` → expected canonical forms;
   `""` and `"   "` rejected; 32 chars accepted, 33 rejected; two tags differing only by
   case are `equals` and collapse in a `Set`; a CJK/emoji tag survives normalization.

### Step 2 — content blocks

4. `domain/content/ContentBlock.java` — `public sealed interface ContentBlock permits
   TextBlock, LinkBlock, ImageBlock {}`
5. `domain/content/TextBlock.java` — `record TextBlock(String text) implements ContentBlock`,
   compact constructor rejecting null (empty string is allowed; a user can have an empty
   paragraph mid-edit).
6. `domain/content/LinkBlock.java` — `record LinkBlock(URI target, String label)`. Reject null
   `target`. Decide: a null `label` becomes `target.toString()` — normalize in the constructor
   so no caller downstream has to null-check.
7. `domain/content/ImageBlock.java` — `record ImageBlock(URI source, String altText)`. Reject
   null `source`; null `altText` becomes `""`.

Keep these dumb. Reachability of the URI is not the domain's business (§7.5: a broken
reference is a *rendering* concern that shows a placeholder — never an exception here).

### Step 3 — Description

8. `domain/model/Description.java`
   ```java
   public record Description(List<ContentBlock> blocks) {
       // TODO: compact constructor — reject null, then blocks = List.copyOf(blocks)
       //       so the record is genuinely immutable AND rejects null elements for free.
       public static Description empty() { return new Description(List.of()); }
       public static Description ofText(String text) { /* TODO single TextBlock */ }

       public String plainTextPreview(int maxChars) {
           // TODO: concatenate TextBlock text only, in order, separated by a single
           //       space; skip Link and Image blocks entirely (§8).
           // TODO: collapse whitespace/newlines so a multi-line block doesn't break
           //       the single-line list row in Phase 3.
           // TODO: if the result is longer than maxChars, truncate to maxChars and
           //       append the ellipsis character "…". Decide explicitly whether
           //       maxChars counts the ellipsis — document it in a javadoc line, and
           //       assert that exact choice in the test. Reject maxChars < 1.
       }
   }
   ```
   **Tests (`DescriptionTest`)**: empty description → `""`; text shorter than the limit is
   returned unchanged with no ellipsis; text longer is truncated per the documented rule; a
   description of `[LinkBlock, TextBlock, ImageBlock]` previews only the text; the list
   returned by `blocks()` is unmodifiable (assert `UnsupportedOperationException` on `add`).

### Step 4 — Idea

9. `domain/model/Idea.java` — per §4.1. Compact constructor null-checks every field and does
   `tags = Set.copyOf(tags)`. Then the five `with*` copy-methods.

   Two things to get right, because everything downstream leans on them:
   - `withUpdatedAt(Instant)` **takes** the instant. If you find yourself writing
     `Instant.now()` inside `Idea`, stop — that is the exact thing that makes Phase 2's
     service tests flaky.
   - No `withCreatedAt`. `createdAt` is set once at construction and preserved by every
     update path (§7.2).

   **Tests (`IdeaTest`)**: each `with*` returns a new instance and leaves the original
   untouched; `withTags` defensively copies (mutate the passed-in set afterwards, assert the
   idea is unaffected); the tags set from `tags()` is unmodifiable; null in any component is
   rejected; two Ideas with equal components are `equals` (record semantics — one assertion
   is enough, you're documenting intent, not testing the JDK).

### Step 5 — commands

10. `domain/command/CreateIdeaCommand.java` — `record CreateIdeaCommand(String title,
    Description description, Set<Tag> tags, IdeaStatus status)`. **No id, no timestamps** (§7.1).
11. `domain/command/UpdateIdeaCommand.java` — same plus `IdeaId id` as the first component.

    These are the one place where "invalid" data legitimately exists — they carry raw form
    input on its way to the validator. So: **do not null-check aggressively in the compact
    constructor**, or the validator can never report "title is missing" as a friendly error.
    Recommended rule: allow a null/blank `title` (the validator's job), but still
    `List.copyOf`/`Set.copyOf` the collections and default a null `description` to
    `Description.empty()` and a null `status` to `INCOMPLETE`.

### Step 6 — validation

12. `domain/validation/ValidationError.java` — `record ValidationError(String field, String
    message)`. `field` is a stable key like `"title"` or `"tags"` so Phase 4 can attach the
    message to the right control instead of string-matching.
13. `domain/validation/ValidationResult.java`
    ```java
    public record ValidationResult(List<ValidationError> errors) {
        // TODO: compact constructor — List.copyOf.
        public static ValidationResult valid() { return new ValidationResult(List.of()); }
        public boolean isValid()   { return errors.isEmpty(); }
        public boolean isInvalid() { return !isValid(); }
        // TODO: errorsFor(String field) helper for the Phase 4 form.
    }
    ```
14. `domain/validation/IdeaValidator.java` — one public method
    `ValidationResult validate(CreateIdeaCommand)` plus an overload for
    `UpdateIdeaCommand` (delegate to a shared private method taking the common fields; the
    only extra rule for update is a non-null `id`).

    Rules to implement — **collect all failures, return them together** (§8 explicitly calls
    out "multiple errors returned at once"; a validator that returns on the first error makes
    the Phase 4 form feel broken):
    - title: not null, not blank (`isBlank()`, **not** `isEmpty()` — the `"   "` edge case in §8)
    - title: length ≤ `TITLE_MAX_LENGTH`
    - each `TextBlock.text`: length ≤ `TEXT_BLOCK_MAX_LENGTH`
    - each `LinkBlock.target` / `ImageBlock.source`: absolute URI with a scheme
      (`uri.isAbsolute()`); a bare `"foo"` typed into a link field is an error
    - tags: count ≤ `MAX_TAGS`; each within `Tag.MAX_LENGTH`
    - status: not null

    **Constants to pick now** (blueprint doesn't specify these; put them as `public static
    final` on `IdeaValidator` so tests reference the constant, not a magic number, and change
    them here if you disagree): `TITLE_MAX_LENGTH = 120`, `TEXT_BLOCK_MAX_LENGTH = 10_000`,
    `MAX_TAGS = 20`.

    **Tests (`IdeaValidatorTest`)**: valid command → `isValid()`; `null`, `""`, `"   "`,
    `"\t\n"` titles each rejected; title at exactly the limit passes and limit+1 fails
    (boundary, both sides); a relative URI in a `LinkBlock` fails; a command with a blank
    title *and* a bad URI returns **two** errors; update with a null id fails.

### Step 7 — query and sort

15. `domain/query/SortOrder.java`
    ```java
    public enum SortOrder {
        NEWEST_FIRST, OLDEST_FIRST;

        public Comparator<Idea> comparator() {
            // TODO: compare on createdAt (reversed for NEWEST_FIRST), then
            //       thenComparing on id.value().toString() as a tiebreak so two
            //       ideas sharing a createdAt instant never jitter between renders.
            //       The tiebreak direction should NOT flip with the sort order —
            //       pick ascending-by-id always, and assert that.
        }
        // TODO: displayName() — Phase 5 populates its ChoiceBox from values().
    }
    ```
16. `domain/query/IdeaQuery.java` — per §7.4.
    ```java
    public record IdeaQuery(Optional<String> titleContains,
                            Set<Tag> anyOfTags,
                            Set<IdeaStatus> anyOfStatus,
                            SortOrder sortOrder) {
        // TODO: compact constructor — null-check, copy the sets.
        public static IdeaQuery all() { /* TODO empty, empty, empty, NEWEST_FIRST */ }

        public Predicate<Idea> toPredicate() {
            // TODO: AND across the three dimensions; empty dimension matches everything.
            // TODO: title match = case-insensitive SUBSTRING match.
            //       Lowercase both sides with Locale.ROOT and use String.contains.
            //       Never compile the input as a regex — a user typing "c++" or "(" must
            //       not blow up (§8 edge cases).
            // TODO: tags = OR within the dimension (decision #4) — the idea matches if it
            //       has ANY of the queried tags. Note this is NOT Set.containsAll.
            // TODO: status = OR within the dimension, same shape as tags.
        }
        // TODO: withTitleContains / withTags / withStatus / withSortOrder copy-methods —
        //       Phase 5's controls each change one dimension at a time.
    }
    ```
    **Tests (`IdeaQueryTest`, `SortOrderTest`)**: `IdeaQuery.all()` matches every fixture;
    each dimension alone filters correctly; two dimensions together AND; two tags OR (an
    idea with only one of them still matches); a search term matching a different case
    matches; a search term containing `.*` and `(` matches literally and throws nothing;
    both sort directions; two ideas with an identical `createdAt` sort deterministically and
    the comparator never returns 0 for distinct ideas (totality — §8).

### Step 8 — test support

Under `src/test/java/com/emgi/timeline/support/`:

17. `FixedClock.java` — thinnest possible: a factory returning
    `Clock.fixed(instant, ZoneOffset.UTC)` plus a small mutable subclass or an
    `AtomicReference`-backed `Clock` with `advance(Duration)`, since Phase 2 needs
    `createdAt != updatedAt` after an update. Plain JDK, no library.
18. `SequentialIdGenerator.java` — deterministic ids (`000...001`, `000...002`, …).
    **Ordering note:** the blueprint puts this in Phase 1, but `IdGenerator` lives in
    `service/` and `service/` is Phase 2. Two clean options — pick one and record it in the
    retro: (a) create the 3-line `service/IdGenerator.java` interface now (it has zero
    dependencies and doesn't drag the service layer forward), or (b) defer
    `SequentialIdGenerator` to Phase 2 and build only `FixedClock` here.
    **Recommended: (a)** — the interface is the seam, and creating it now costs nothing.
19. `IdeaFixtures.java` — a builder with sane defaults (`anIdea()`, `.withTitle(...)`,
    `.withTags(...)`, `.createdAt(...)`, `.build()`). Every test above should build its data
    through this, not with 7-argument `new Idea(...)` calls. This is the file that decides
    whether Phases 2–6's tests are pleasant to write.

### Step 9 — the architecture guard

20. `src/test/java/com/emgi/timeline/domain/DomainPurityTest.java` — no new dependency needed:
    walk `src/main/java/com/emgi/timeline/domain` with `Files.walk`, read each `.java` file,
    and assert no line starts with `import javafx.` or `import java.sql.`. Fail with the
    offending file and line in the message.

    Worth the ~20 lines: the "domain imports no framework" rule is the single constraint
    most likely to be broken by a rushed edit in Phase 4 or 6, and a code review won't catch
    it as reliably as a red test will.

---

## Files created / modified
_(fill in as you go)_

## Tests written
- [ ] `TagTest`, `DescriptionTest`, `IdeaTest`
- [ ] `IdeaValidatorTest`
- [ ] `IdeaQueryTest`, `SortOrderTest`
- [ ] `DomainPurityTest`
- [ ] Manual smoke check: n/a — this phase has no UI. The check is `mvn test` green and
      `grep -r "javafx\|java.sql" src/main/java/com/emgi/timeline/domain` returning nothing.

## Definition of done
Full unit-test coverage of every rule in §8 that doesn't need a repository; **zero framework
imports in `domain/`**, enforced by a test rather than by discipline. Not "mostly covered" —
if a §8 row has no assertion behind it, the phase isn't closed.

## Retro (fill in when the phase is closed)
- Estimated: 3–5 h — Actual: ___
- What took longer than expected, and why:
- Any deviation from ARCHITECTURE.md (and whether the blueprint should be updated to match):
  - _Pre-registered:_ `IdeaValidator`'s three length constants aren't in the blueprint — if
    you keep 120 / 10 000 / 20, add them to §4 so Phase 4's form hints match.
  - _Pre-registered:_ the `SequentialIdGenerator` / `IdGenerator` phase-boundary question
    from Step 8.
- Anything punted to a later phase that wasn't originally planned that way:
