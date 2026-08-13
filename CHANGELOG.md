# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Changed
- Migrated the build system from Gradle to Maven, for consistency with other
  ICIJ projects. Use `./mvnw package` instead of `./gradlew build`; the fat
  jar is now produced at `target/intellij-code-formatter.jar` instead of
  `build/libs/vscode-idea-code-formatter.jar`.

### Breaking
- The bundled VSCode extension (`vscode-extension/`) is currently **incompatible**
  with this CLI change. It still invokes the formatter JAR with the old
  single-file `--lines`/`--style` contract, which no longer works now that the
  CLI is directory-only — every format command run from the extension will
  fail with exit code `2` ("Not a directory"). This is a known, accepted gap;
  updating the extension to the new directory-based contract is tracked as
  separate follow-up work and has not been done yet. Do not rely on the
  bundled extension until that follow-up lands.

### Changed
- CLI now takes a directory instead of a single file, recursively formatting
  every `.java` file found (skipping `.git`, `build`, `target`, `out`, and
  `node_modules` at any depth)
- Removed `--lines` CLI option (formatting a line range within one file no
  longer composes with directory-wide operation); `StandaloneFormatter.formatCodeRange`
  is still available for programmatic use
- Restructured the repository into a two-module Maven reactor:
  `formatter-core` (the existing CLI/library, unchanged) and the new
  `maven-plugin`.

### Added
- `--check` CLI flag: verifies formatting without writing changes, for use
  as a CI gate (exit code `1` if any file is not formatted correctly)
- New `intellij-code-formatter-maven-plugin` module: a Maven plugin with
  `check` (lint, fails the build) and `format` (auto-fix) goals, both bound
  by default to `process-sources`, using a codestyle fixed in the plugin's
  own resources. See the README's "Maven Plugin" section.

## [2025.3.2] - 2026-02-05

### Fixed
- Fixed critical bug where files with CRLF (Windows) line endings would not format correctly
- Fixed range formatting not working with CRLF line endings
- Improved line ending detection and preservation
  - Formatter now detects original line endings (CRLF, LF, CR)
  - Normalizes to LF internally for IntelliJ Platform compatibility
  - Converts back to original line endings after formatting
- Fixed `getLineStartOffset()` and `getLineEndOffset()` methods to handle all line ending types

### Technical Details
- Added `detectLineEnding()` method to identify line ending style
- Added `normalizeLineEndings()` to convert all line endings to LF
- Added `convertLineEndings()` to restore original line endings after formatting
- Updated offset calculation methods to correctly handle CRLF sequences

## [2025.3.1] - Previous Release

Initial release with IntelliJ 2025.3.1 formatting engine support.
