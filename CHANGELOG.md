# Changelog

All notable changes to Timeline are recorded here. 

## [1.1.0] — 2026-08-30

### Added

- **By progress** ordering, now the default: ideas are grouped In progress → Incomplete → Completed, newest first inside each group.
- Status emphasis in the idea list: in-progress rows are raised onto the top and highlighted, completed rows are dimmed.
- **Show** — a status filter in the sort menu that narrows the list to one status at a time (Incomplete, In progress, or Completed).

### Changed

- **Newest first and Oldest first now order by date alone.** They were previously grouped by status underneath the date ordering.
- Faster cold start, via AppCDS and `-XX:TieredStopAtLevel=1` in the packaged app's java options.
- The portable release zip is no longer tracked in the repository; it is published as a GitHub Release asset instead.

### Known issues

- A recycled row in the idea list can briefly keep the dimmed styling of a completed idea it no longer shows. This is cosmetic only, no idea's data or status is affected. Fixed in 1.1.1.

## [1.0.0] — 2026-08-27

First public release: a desktop app for capturing and organizing ideas in one
list, stored locally in SQLite with no account and no network access.

### Added

- Create, edit, and delete ideas.
- Status tracking: Incomplete, In progress, Completed.
- Free-form tags, with filtering by any combination of them.
- Live title search and newest/oldest sorting.
- Descriptions with bullet lists, automatic link detection, and inline images.
- Dark and light themes, with an animations toggle.
- Portable Windows x64 build produced with jpackage; data lives in
  `%USERPROFILE%\.timeline\` and survives install and removal.

[1.1.0]: https://github.com/maxey05/timeline/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/maxey05/timeline/releases/tag/v1.0.0
