# Timeline

<img width="800" height="400" alt="Image" src="https://github.com/user-attachments/assets/b8bef9ef-e41d-46ea-95cb-31ec542d7c00" />

A desktop app that serves as your second brain- organizing and managing your ideas.

![Version](https://img.shields.io/badge/version-1.0.0-lightgrey)
![Java](https://img.shields.io/badge/Java-21-lightgrey)
![Platform](https://img.shields.io/badge/platform-Windows%20x64-lightgrey)

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
  - [Running the released app](#running-the-released-app)
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

## Overview

Timeline is a desktop application for keeping track of ideas — giving you a platform to deliberate on each thought and making sure you complete each one. Every idea has a title,
a status, any number of tags and a free-form description that can hold text,
links, and images. Everything is stored locally in a SQLite file
in your home folder; no account, no server and no network traffic.

**What timeline does for you**

- Create, edit and delete ideas, with validation on the title.
- Track each idea's progress level.
- Tag ideas freely and filter the list by any combination of tags.
- Search titles as you type, or sort by newest or oldest.
- Write descriptions with bullet lists, automatic link detection, and images.
- Switch between a dark and a light theme.

---

## Requirements

### Running the released app

| | |
|---|---|
| Operating system | Windows 10 or 11, 64-bit |
| Disk space | ~120 MB extracted |

---

## Installation

### Option A — download the release

1. Go to the [Releases](../../releases) page and download
   `Timeline-1.0.0-win-x64.zip` from the latest release.
2. Extract the zip somewhere you can keep it — `C:\Program Files\Timeline` or
   just a folder in your user directory. The app runs from wherever you put it.
3. Run **`Timeline.exe`** from inside the extracted `Timeline` folder.

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
├── timeline.db      SQLite database
└── images\          copies of every image you paste or insert
```

Backing up Timeline means copying that folder. Moving to a new machine means copying it across. The folder is created automatically on first launch.

---

## Using timeline

### The main window

Your ideas appear as a single list. Click a row to open its **detail panel** — a read-only view will all the details concerning your idea. Press `Esc` or click outside the panel to dismiss it; your place in the list is kept.

Right-click an idea row to **Edit** or **Delete** an idea.

### Creating and editing an idea

Click **+ New Idea**, or press `Ctrl+N`. The editor opens as an external panel.

| Field | Notes |
|---|---|
| **Title** | Required.|
| **Status** | *Incomplete*, *In progress* or *Completed* |
| **Tags** | Type a tag and press `+` or `Enter` to add it. Click a tag to remove it |
| **Description** | Free-form. See below |

Press `Ctrl+Enter` or click **Save** to commit. `Esc` or **Cancel** closes the editor. If you have unsaved changes it asks before discarding them.

### Writing a description

The description is one flowing text field with a few conveniences layered on:

- **Bullets** — start a line with `- ` and it renders as a bullet. Pressing `Enter` at the end of a bullet continues the list; pressing it again on an empty bullet ends it.
- **Links** — URLs are detected automatically and become clickable in the detail view.
- **Images** — press `Ctrl+I` to pick one or more image files, or copy an image anywhere on your system and press `Ctrl+V`. The picture appears inline in the text as you type around it.

  Images are **copied** into `.timeline\images\`, so moving or deleting the original file afterwards will not break the idea. Supported formats: PNG, JPG, JPEG, GIF, BMP.

### Search functions

- **Search** (`Ctrl+F`) filters the list by title as you type.
- **Tag chips** sit above the list. Click any number of them to narrow the list to ideas carrying *all* of the selected tags. The **All** chip resets the selection. Chips are built from the tags actually in use, so the row changes as your tags do.
- **Sort** switches between *Newest first* and *Oldest first*.
- **Clear filters** resets search, tags and sort in one click.

### Settings

Open settings from the gear in the header.

| Setting | What it does |
|---|---|
| **Your name** | Shown in the window greeting. Leave it blank to keep the current name |
| **Dark theme** | On by default. Turn it off for the light theme |
| **Animations** | Controls overlay fades and the greeting reveal. Turn it off if you prefer things instant |

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

Some updates to the program that I plan on implementing:


- **Favourites / pinning** — a star on each idea and a matching filter chip.
- **Status filter** — filter by *Incomplete* / *In progress* / *Completed*.
- **More sort orders** — by title, by last modified
- **Export and import** — Markdown and JSON, so ideas can leave the app
- **Undo / redo** in the editor
- **Deadlines** — set a deadline for an idea

