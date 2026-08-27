# Timeline

A minimal desktop app for capturing, organising and revisiting ideas.

![Version](https://img.shields.io/badge/version-1.0.0-lightgrey)
![Java](https://img.shields.io/badge/Java-21-lightgrey)
![Platform](https://img.shields.io/badge/platform-Windows%20x64-lightgrey)

---

## Overview

Timeline is a single-user desktop application for keeping track of ideas — the
half-formed ones you would otherwise lose in a notes app. Every idea has a title,
a status, any number of tags and a free-form description that can hold text,
bullets, links and inline images. Everything is stored locally in a SQLite file
in your home folder; there is no account, no server and no network traffic.

The interface is deliberately quiet: a monochrome theme, a custom window frame,
one list, and overlays instead of extra windows. It is built to be usable
entirely from the keyboard.

**What it does**

- Create, edit and delete ideas, with validation on the title
- Track each idea as *Incomplete*, *In progress* or *Completed*
- Tag ideas freely and filter the list by any combination of tags
- Search titles as you type, and sort by newest or oldest first
- Write descriptions with bullet lists, automatic link detection, and images
  pasted or inserted straight into the text
- Remember your window size, position and settings between runs
- Switch between a dark and a light theme, and turn animations off

**What it deliberately does not do**

No cloud sync, no accounts, no collaboration, no rich-text toolbar. Timeline is
a capture tool, not a word processor.

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
  - [Running the released app](#running-the-released-app)
  - [Building from source](#building-from-source)
- [Installation](#installation)
  - [Option A — download the release](#option-a--download-the-release)
  - [Option B — build and run from source](#option-b--build-and-run-from-source)
  - [Where your data lives](#where-your-data-lives)
- [Using Timeline](#using-timeline)
  - [The main window](#the-main-window)
  - [Creating and editing an idea](#creating-and-editing-an-idea)
  - [Writing a description](#writing-a-description)
  - [Finding things again](#finding-things-again)
  - [Settings](#settings)
  - [Keyboard shortcuts](#keyboard-shortcuts)
- [Future Updates Planned](#future-updates-planned)
- [License](#license)

---

## Requirements

### Running the released app

| | |
|---|---|
| Operating system | Windows 10 or 11, 64-bit |
| Java | **Not required** — a Java runtime is bundled inside the download |
| Disk space | ~120 MB extracted |
| Network | None. Timeline never connects to anything |

### Building from source

| | |
|---|---|
| JDK | Java **21** (any distribution — Temurin, Oracle, Liberica). `jpackage` ships with it |
| Build tool | Apache Maven **3.9** or newer |
| Network | Needed once, to download dependencies from Maven Central |

Everything else — JavaFX 21, SQLite (via `sqlite-jdbc`), RichTextFX — is resolved
by Maven. There is no separate JavaFX SDK to install.

---

## Installation

### Option A — download the release

1. Go to the [Releases](../../releases) page and download
   `Timeline-1.0.0-win-x64.zip` from the latest release.
2. Extract the zip somewhere you can keep it — `C:\Program Files\Timeline` or
   just a folder in your user directory. The app runs from wherever you put it.
3. Run **`Timeline.exe`** from inside the extracted `Timeline` folder.

> **Windows SmartScreen.** The executable is not code-signed, so the first launch
> may show *"Windows protected your PC"*. Click **More info → Run anyway**. This
> is expected for an unsigned open-source build.

To uninstall, delete the extracted folder. Your ideas are stored separately (see
[Where your data lives](#where-your-data-lives)) and will survive.

### Option B — build and run from source

```bash
git clone https://github.com/maxey05/timeline.git
cd timeline

mvn test          # run the test suite
mvn javafx:run    # launch the app
```

To produce your own portable build:

```bash
mvn clean package
jpackage --type app-image --name Timeline --app-version 1.0.0 \
         --input target/dist --main-jar timeline-1.0.0.jar \
         --main-class com.emgi.timeline.Launcher \
         --icon packaging/icon.ico --dest target/package
```

### Where your data lives

Timeline keeps everything under a single folder in your home directory:

```
%USERPROFILE%\.timeline\
├── timeline.db      SQLite database — every idea, tag and description
└── images\          copies of every image you paste or insert
```

Backing up Timeline means copying that folder. Moving to a new machine means
copying it across. The folder is created automatically on first launch.

---

## Using Timeline

### The main window

The window has its own frame rather than the standard Windows one, but it
behaves normally: drag the top strip to move it, double-click that strip or use
the ▣ button to maximise, and drag any edge or corner to resize.

Your ideas appear as a single list. Click a row to open its **detail panel** — a
read-only view over a dimmed background, with an **Edit…** button. Press `Esc`
or click outside the panel to dismiss it; your place in the list is kept.

Hover a row (or select it with the arrow keys) and a small **action tray** slides
in from the right with **Edit** and **Delete**. Deleting asks for confirmation
first, and the confirmation defaults to *Cancel*.

### Creating and editing an idea

Click **+ New Idea**, or press `Ctrl+N`. The editor opens as an overlay inside
the window, not a separate one.

| Field | Notes |
|---|---|
| **Title** | Required. Leading and trailing spaces are trimmed. An empty title is rejected with an inline error |
| **Status** | *Incomplete*, *In progress* or *Completed* |
| **Tags** | Type a tag and press `+` or `Enter` to add it. Tags are normalised, so `Design` and `design` are the same tag. Click a tag to remove it |
| **Description** | Free-form. See below |

Press `Ctrl+Enter` or click **Save** to commit. `Esc` or **Cancel** closes the
editor — if you have unsaved changes it asks before discarding them.

### Writing a description

The description is one flowing text field with a few conveniences layered on:

- **Bullets** — start a line with `- ` and it renders as a bullet. Pressing
  `Enter` at the end of a bullet continues the list; pressing it again on an
  empty bullet ends it.
- **Links** — anything that looks like a URL (`https://…`, `http://…`, `www.…`,
  `file://…`) is detected automatically and becomes clickable in the detail view.
  There is no link syntax to remember.
- **Images** — press `Ctrl+I` to pick one or more image files, or copy an image
  (or image file) anywhere on your system and press `Ctrl+V`. The picture appears
  inline in the text as you type around it.

  Images are **copied** into `.timeline\images\`, so moving or deleting the
  original file afterwards will not break the idea. Supported formats: PNG, JPG,
  JPEG, GIF, BMP.

Underneath, a description is stored as a single string — images are written as
`![alt](file:///…)` tokens on their own line — so nothing is locked into a
proprietary format.

### Finding things again

- **Search** (`Ctrl+F`) filters the list by title as you type. `Esc` clears it.
- **Tag chips** sit above the list. Click any number of them to narrow the list
  to ideas carrying *all* of the selected tags. The **All** chip resets the
  selection. Chips are built from the tags actually in use, so the row changes as
  your tags do.
- **Sort** switches between *Newest first* and *Oldest first*.
- **Clear filters** resets search, tags and sort in one click.

Two different empty states tell you which situation you are in: *"No ideas yet"*
when the database is empty, and *"No ideas match your filters"* when your filters
are simply too narrow.

### Settings

Open settings from the gear in the header.

| Setting | What it does |
|---|---|
| **Your name** | Shown in the window greeting. Leave it blank to keep the current name |
| **Dark theme** | On by default. Turn it off for the light theme |
| **Animations** | Controls overlay fades and the greeting reveal. Turn it off if you prefer things instant |

Settings persist between runs, alongside your window size and position.

### Keyboard shortcuts

**Main window**

| Shortcut | Action |
|---|---|
| `Ctrl` + `N` | New idea |
| `Ctrl` + `F` | Jump to search |
| `↑` / `↓` | Move through the list |
| `Enter` | Open the selected idea's detail panel |
| `Esc` | Close the open overlay, clear the search, or clear the selection |
| `Tab` | Cycle focus within whichever overlay is open |

**Editor**

| Shortcut | Action |
|---|---|
| `Ctrl` + `Enter` | Save |
| `Esc` | Cancel (asks first if there are unsaved changes) |
| `Ctrl` + `I` | Insert image from file |
| `Ctrl` + `V` | Paste image from clipboard (in the description field) |

---

## Future Updates Planned

Ordered roughly by how likely they are to land next.

**Near term**

- **Favourites / pinning** — a star on each idea and a matching filter chip
- **Status filter** — filter by *Incomplete* / *In progress* / *Completed*, which
  the data model already supports but the UI does not expose
- **Full-text search of descriptions**, not just titles
- **More sort orders** — by title, by last modified

**Packaging and platforms**

- **Windows MSI installer** with a Start Menu entry and a proper uninstaller
- **macOS and Linux builds** — the code is platform-neutral; only the packaging
  step is Windows-specific today
- **Code signing**, to remove the SmartScreen warning

**Larger**

- **Export and import** — Markdown and JSON, so ideas can leave the app
- **Undo / redo** in the editor
- **Drag-and-drop reordering** of images within a description
- **Schema migrations**, so the database can evolve without manual intervention
- **Optional sync** — the storage layer sits behind an interface with two
  implementations already, so a third, sync-backed one is a known-shaped change
  rather than a rewrite

**Not planned**

Accounts, collaboration, a full WYSIWYG editor, or video playback. These would
change what Timeline is.

---

## License

*(To be chosen — MIT is the usual pick for a project like this.)*
