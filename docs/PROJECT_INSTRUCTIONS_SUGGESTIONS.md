# Suggested Claude Project instructions — Timeline

Paste some or all of this into the project's custom instructions. It's written to do one job: stop future sessions (including me, next time) from re-deciding things that are already decided, or drifting outside the architecture without anyone noticing until it hurts.

```
This project is "Timeline" — a Java 21 / JavaFX 21 desktop idea-management app.
docs/ARCHITECTURE.md is the source of truth for design decisions; docs/PHASE_TEMPLATE.md
tracks phase-by-phase progress. Read the relevant sections before generating code,
don't restate them back to me as if they were new questions.

Architecture guardrails — treat these as hard constraints, not suggestions:
- domain/ never imports javafx.* or java.sql.* — including in "just this once" cases.
  If a task seems to require that, stop and flag it instead of doing it.
- Controllers (controller/) may use javafx.beans / javafx.collections but never
  javafx.scene / javafx.stage. That's what keeps them testable without booting JavaFX.
- Package root is com.emgi.timeline.
- SqliteIdeaRepository is what the app runs on. InMemoryIdeaRepository exists only
  for tests — never wire it into App.java, even temporarily "to test faster."
- No module-info.java / no jlink (sqlite-jdbc isn't a JPMS module — see
  ARCHITECTURE.md §11 Risk 5). Packaging uses jpackage in classpath mode.

Workflow:
- Follow the phase order in ARCHITECTURE.md §9: domain → repository/service →
  read-only UI → CRUD → organization → rich content → polish → packaging.
  Don't build UI ahead of the domain layer having tests, even if it would be
  faster to "just see something on screen."
- Before starting a phase, check docs/PHASE_TEMPLATE.md's quick-reference table
  for what's in/out of scope. Rich-content editing (Phase 6) is the one most
  likely to scope-creep into a full WYSIWYG editor — see §11 Risk 1. If a request
  sounds like it's heading there, say so before building it.
- When a phase wraps up, fill in that phase's retro section (estimate vs. actual,
  what deviated from the blueprint). If something deviated, ask whether
  ARCHITECTURE.md should be updated to match, rather than letting the doc go stale.

Code style:
- Default to a skeleton with TODO comments, not a finished implementation, unless
  I ask for working code.
- New unit tests ship in the same response as new domain/service/repository code —
  don't defer testing to "later."
- Use JUnit 5 + AssertJ. Repository tests are written once against the
  IdeaRepository interface and run against both implementations (contract test
  pattern in ARCHITECTURE.md §8), not duplicated per implementation.
- When compiling or running Java in a sandbox, check for javac first and install
  openjdk-21-jdk-headless if it's missing — don't ask, just do it.
- Tell me which file each piece of code belongs in.
```

A few of these overlap with your general Claude preferences (skeleton-first, javac check, specify the file) — repeating them here isn't redundant, it's what makes them apply reliably to a project where "just this once, write the whole thing" is a real temptation (e.g. Phase 6's block editor). Project-level instructions win ties over general habits when a session is moving fast.

One thing deliberately left out: I didn't encode the full list of 8 locked decisions from `ARCHITECTURE.md` §0 into this instruction set, because that table will stop being "current" the moment V1 ships and you start making V2 decisions. Point future sessions at §0 rather than duplicating it here — one source of truth beats two that can drift apart.
