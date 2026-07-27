# Directory Traversal + Format Check — Design

## Context

The CLI (`JetbrainsFormatterApplication`) currently formats exactly one file
per invocation, passed as a positional argument, with optional `--style`
and `--lines` flags. This spec covers two changes:

1. Replace the single-file argument with a directory argument: recursively
   find all `.java` files under it and format them in place.
2. Add a `--check` mode that verifies files are already correctly formatted
   without writing changes, suitable for CI gating (exit code communicates
   pass/fail).

`--lines` and single-file support are removed — they don't compose with
directory-wide operation and add no value once the CLI always operates on
a tree.

## Scope

- Java files only (`*.java`). Other supported languages (Kotlin, XML,
  JSON, YAML, Groovy, Properties, HTML) are out of scope for traversal —
  can be added later by widening `JavaFileTraverser` if needed.
- CLI-only feature. No change to the public `StandaloneFormatter` API,
  which remains usable as a library for single-file/string formatting.

## Components

### `JavaFileTraverser` (new, `com.intellij.formatter.core`)

```java
public static List<Path> findJavaFiles(Path root) throws IOException
```

- Walks the directory tree rooted at `root`.
- Skips any subtree whose directory name is exactly `.git`, `build`,
  `target`, `out`, or `node_modules` — matched at any depth, not just the
  top level. The excluded directory itself is not descended into.
- Collects files whose name ends with `.java`.
- Returns paths in a deterministic (sorted) order.

### `DirectoryFormatter` (new, `com.intellij.formatter.core`)

```java
public static FormatReport format(Path directory) throws IOException
public static FormatReport check(Path directory) throws IOException
```

- Both methods call `JavaFileTraverser.findJavaFiles`, then for each file:
  read content, run it through `StandaloneFormatter.formatCode`, catching
  `FormattingException` per file (recorded as a failure; traversal
  continues — one bad file does not abort the run).
- `format`: if the formatted content differs from the original, write it
  back; record the path in `changed`.
- `check`: never writes; if the formatted content differs from the
  original, record the path in `changed` (meaning "not compliant").
- `FormatReport` — a record: `List<Path> changed`, `Map<Path, String>
  failures` (path → exception message), `int totalFiles`.

### `JetbrainsFormatterApplication` (modified)

- Argument parsing: positional `<directory>` (required), `--style/-s
  <path>`, `--check`, `--help/-h`, `--version/-v`. `--lines` is removed.
- Validates the positional argument exists and is a directory; otherwise
  prints a usage error and exits `2` before any traversal.
- Initializes the formatting engine and optionally loads a custom code
  style exactly as today (a style load failure is a warning, not fatal).
- Calls `DirectoryFormatter.format(directory)` or `.check(directory)`
  depending on `--check`.
- Output:
  - Format mode: prints `Formatted: <path>` for each changed file, then a
    summary line (`Formatted N of M files`).
  - Check mode: prints each non-compliant path, then a summary line
    (`N of M files are not formatted correctly`).
  - Both modes: prints any per-file failures at the end, regardless of
    mode, as `Failed: <path> — <message>`.
- Exit codes:
  - `0` — success: format mode completed with no failures, or check mode
    found everything compliant with no failures.
  - `1` — check mode found non-compliant files, and there were no hard
    failures.
  - `2` — hard error: bad/missing directory argument, or at least one
    file failed to parse/format. Takes priority over `1`.

## Error Handling

- Missing or non-directory positional argument → usage error, exit `2`,
  no traversal attempted.
- Per-file `FormattingException` → caught in `DirectoryFormatter`,
  recorded in `FormatReport.failures`, run continues.
- Code style load failure → warning printed, run continues with default
  style (unchanged from current behavior).

## Testing

- `JavaFileTraverserTest`: nested directories, excluded directory names
  at various depths, empty directory, directory with no `.java` files.
- `DirectoryFormatterTest`: `@TempDir`-based tests covering a mix of
  already-formatted, unformatted, and syntactically invalid `.java` files
  for both `format` and `check`, asserting `FormatReport` contents and
  (for `format`) that files were actually rewritten on disk.
- No test drives `JetbrainsFormatterApplication.main` directly, matching
  existing test coverage (which tests `StandaloneFormatter` directly, not
  the CLI entry point).

## Out of Scope / Follow-ups

- Non-Java file types in directory traversal.
- Parallelizing file formatting (not needed at expected project sizes).
- `README.md` / `scripts/idea-format` usage text updates — should be
  updated as part of implementation to match the new CLI contract, but
  are not a design concern.
