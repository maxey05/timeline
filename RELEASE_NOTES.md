## Timeline 1.1.0

Timeline is a desktop app for capturing and organizing ideas — one list, tags,
statuses, and a description field that supports bullets, auto-detected links,
and inline images. Everything is stored locally in SQLite; there's no account
and no network access.

This release changes what the list looks like when you open it. Ideas now sort
by how far along they are rather than by date alone, so the things you're
actually working on sit at the top, everything you haven't started follows, and
finished ideas settle at the bottom, dimmed. You can read the list as *working
on / waiting / done* without scanning a single status label.

### What's new

- **By progress** is the new default order: In progress, then Incomplete, then
  Completed, newest first within each group.
- In-progress ideas are raised and highlighted; completed ones are dimmed. Both
  follow the dark and light themes.
- **Show** lets you narrow the list to one status at a time — Incomplete, In
  progress, or Completed — from the same menu as the sort order.
- The app starts noticeably faster from cold.

### Heads-up if you're upgrading

**Newest first** and **Oldest first** now order by date alone. They used to be
grouped by status underneath, so your list may look different after upgrading
even though nothing was renamed and nothing was lost. Choose **By progress** for
the grouped behaviour.

### Known issue

While scrolling, or right after switching the **Show** filter, a row can briefly
keep the dimmed styling of a completed idea it no longer shows. It's cosmetic —
no idea's data or status is affected, and scrolling past the row or reopening
the list clears it. A fix is coming in 1.1.1.

### Download

Grab `Timeline-1.1.0-win-x64.zip` below, extract it anywhere, and run
`Timeline.exe`.

This build isn't code-signed, so Windows SmartScreen may show *"Windows
protected your PC"* on first launch. Click **More info → Run anyway**.

### Upgrading from 1.0.0

Extract the new zip and delete the old `Timeline` folder — that's the whole
upgrade. Your ideas live outside the app (see **Data** below), so the new build
picks them up automatically and there's no migration step.

### Data

Your ideas are stored in `%USERPROFILE%\.timeline\` (database + any pasted
images). That folder isn't touched by installing or removing the app, and it's
what you'd back up or carry to another machine.

### Requirements

Windows 10/11, 64-bit. See the [README](https://github.com/maxey05/timeline#readme)
for full usage details and what's planned next.
