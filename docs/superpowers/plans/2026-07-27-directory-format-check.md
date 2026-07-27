# Directory Traversal + Format Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change the CLI from formatting one file at a time to recursively formatting (or checking) every `.java` file under a directory.

**Architecture:** A new `JavaFileTraverser` walks a directory tree and returns the list of `.java` files, skipping common build/vendor directories. A new `DirectoryFormatter` runs each found file through the existing `StandaloneFormatter`, either writing changes back (`format`) or only reporting differences (`check`), collecting per-file failures without aborting the run. `JetbrainsFormatterApplication` is rewired to take a directory argument, add `--check`, and drop `--lines` and single-file support.

**Tech Stack:** Java 21, JUnit 5.9.2, Lombok 1.18.30 (`@UtilityClass` for static-only classes), Gradle (wrapper).

## Global Constraints

- Target Java 21 language level (`build.gradle.kts` toolchain).
- New static-utility classes use Lombok's `@UtilityClass` (see `StandaloneFormatter` for the existing convention) rather than a private constructor + `static` boilerplate.
- New classes live in `com.intellij.formatter.core`, matching `StandaloneFormatter`, `FormattingException`, `CodeStyleLoadException`.
- Excluded directory names, matched at any depth: `.git`, `build`, `target`, `out`, `node_modules`.
- CLI output convention: progress/diagnostic/error lines go to `System.err` (matches existing `Initializing...`, `Engine initialized` lines); user-facing result data (changed-file lists, summary counts, `--help`/`--version` text) goes to `System.out` so it can be piped/captured in CI.
- Exit codes: `0` success, `1` `--check` found non-compliant files (no hard errors), `2` hard error (bad directory argument, or any file failed to parse/format) — `2` takes priority over `1`.
- Tests use JUnit 5's `@TempDir` for filesystem fixtures, matching no existing precedent needed to override (this project currently tests `StandaloneFormatter` with in-memory strings only; these are the first filesystem-based tests).

---

### Task 1: `JavaFileTraverser`

**Files:**
- Create: `src/main/java/com/intellij/formatter/core/JavaFileTraverser.java`
- Test: `src/test/java/com/intellij/formatter/core/JavaFileTraverserTest.java`

**Interfaces:**
- Produces: `public static List<Path> findJavaFiles(Path root) throws IOException` — recursively finds `.java` files under `root`, skipping subtrees whose directory name is `.git`, `build`, `target`, `out`, or `node_modules` at any depth. Returns paths sorted in natural `Path` order.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/intellij/formatter/core/JavaFileTraverserTest.java`:

```java
package com.intellij.formatter.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JavaFileTraverser Tests")
class JavaFileTraverserTest {

    @Test
    @DisplayName("finds .java files nested in subdirectories")
    void findsNestedJavaFiles(@TempDir Path tempDir) throws IOException {
        var top = tempDir.resolve("Top.java");
        Files.writeString(top, "class Top {}");

        var nestedDir = tempDir.resolve("a/b");
        Files.createDirectories(nestedDir);
        var nested = nestedDir.resolve("Nested.java");
        Files.writeString(nested, "class Nested {}");

        var found = JavaFileTraverser.findJavaFiles(tempDir);

        assertEquals(2, found.size());
        assertEquals(Set.of(top, nested), new HashSet<>(found));
    }

    @Test
    @DisplayName("ignores non-.java files")
    void ignoresNonJavaFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Keep.java"), "class Keep {}");
        Files.writeString(tempDir.resolve("Ignore.txt"), "not java");

        var found = JavaFileTraverser.findJavaFiles(tempDir);

        assertEquals(List.of(tempDir.resolve("Keep.java")), found);
    }

    @Test
    @DisplayName("skips excluded directories at the top level")
    void skipsExcludedDirectoriesAtTopLevel(@TempDir Path tempDir) throws IOException {
        var buildDir = tempDir.resolve("build");
        Files.createDirectories(buildDir);
        Files.writeString(buildDir.resolve("Generated.java"), "class Generated {}");
        Files.writeString(tempDir.resolve("Real.java"), "class Real {}");

        var found = JavaFileTraverser.findJavaFiles(tempDir);

        assertEquals(List.of(tempDir.resolve("Real.java")), found);
    }

    @Test
    @DisplayName("skips excluded directories nested at any depth")
    void skipsExcludedDirectoriesAtAnyDepth(@TempDir Path tempDir) throws IOException {
        var nestedTarget = tempDir.resolve("src/main/target");
        Files.createDirectories(nestedTarget);
        Files.writeString(nestedTarget.resolve("Generated.java"), "class Generated {}");
        var real = tempDir.resolve("src/main/Real.java");
        Files.writeString(real, "class Real {}");

        var found = JavaFileTraverser.findJavaFiles(tempDir);

        assertEquals(List.of(real), found);
    }

    @Test
    @DisplayName("returns an empty list for an empty directory")
    void returnsEmptyListForEmptyDirectory(@TempDir Path tempDir) throws IOException {
        assertTrue(JavaFileTraverser.findJavaFiles(tempDir).isEmpty());
    }

    @Test
    @DisplayName("returns files in sorted order")
    void returnsFilesInSortedOrder(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Zebra.java"), "class Zebra {}");
        Files.writeString(tempDir.resolve("Apple.java"), "class Apple {}");

        var found = JavaFileTraverser.findJavaFiles(tempDir);

        assertEquals(List.of(tempDir.resolve("Apple.java"), tempDir.resolve("Zebra.java")), found);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.intellij.formatter.core.JavaFileTraverserTest"`
Expected: compilation failure — `JavaFileTraverser` does not exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/intellij/formatter/core/JavaFileTraverser.java`:

```java
package com.intellij.formatter.core;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Recursively finds {@code .java} files under a directory, skipping common
 * build/vendor directories that may contain generated or third-party code.
 */
@UtilityClass
public class JavaFileTraverser {

    private static final Set<String> EXCLUDED_DIR_NAMES =
            Set.of(".git", "build", "target", "out", "node_modules");

    /**
     * Finds all {@code .java} files under {@code root}, skipping any subtree
     * rooted at a directory named {@code .git}, {@code build}, {@code target},
     * {@code out}, or {@code node_modules}, at any depth.
     *
     * @param root the directory to search
     * @return matching files in sorted order
     * @throws IOException if the directory tree cannot be walked
     */
    public static List<Path> findJavaFiles(Path root) throws IOException {
        var results = new ArrayList<Path>();

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && EXCLUDED_DIR_NAMES.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".java")) {
                    results.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(results);
        return results;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.intellij.formatter.core.JavaFileTraverserTest"`
Expected: all 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/intellij/formatter/core/JavaFileTraverser.java src/test/java/com/intellij/formatter/core/JavaFileTraverserTest.java
git commit -m "Add JavaFileTraverser for recursive .java file discovery"
```

---

### Task 2: `FormatReport` and `DirectoryFormatter`

**Files:**
- Create: `src/main/java/com/intellij/formatter/core/FormatReport.java`
- Create: `src/main/java/com/intellij/formatter/core/DirectoryFormatter.java`
- Test: `src/test/java/com/intellij/formatter/core/DirectoryFormatterTest.java`

**Interfaces:**
- Consumes: `JavaFileTraverser.findJavaFiles(Path root) throws IOException` (Task 1); `StandaloneFormatter.formatCode(String code, String fileName) throws FormattingException` (existing).
- Produces:
  - `record FormatReport(int totalFiles, List<Path> changed, Map<Path, String> failures)`
  - `public static FormatReport DirectoryFormatter.format(Path directory) throws IOException` — reformats every `.java` file found under `directory`, writing back only files whose content changed.
  - `public static FormatReport DirectoryFormatter.check(Path directory) throws IOException` — same pass, never writes; `changed` lists files that are not currently formatted correctly.
  - In both, a file that throws `FormattingException` is recorded in `failures` (path → `e.getMessage()`) and the run continues with the remaining files. `IOException` from reading/writing a file is NOT caught — it propagates and aborts the run (this is intentional; see plan Task 3 note on exit code `2`).

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/intellij/formatter/core/DirectoryFormatterTest.java`:

```java
package com.intellij.formatter.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DirectoryFormatter Tests")
class DirectoryFormatterTest {

    // Structurally identical to JavaFormatterTest's verified "simple class with method"
    // fixture (renamed), so this is a known-idempotent formatted shape.
    private static final String CLEAN_CONTENT = """
            public class Clean {
                void go() {
                    int x = 1;
                }
            }""";

    @Test
    @DisplayName("format() rewrites unformatted files and leaves formatted files untouched")
    void formatRewritesOnlyUnformattedFiles(@TempDir Path tempDir) throws IOException {
        var unformatted = tempDir.resolve("Messy.java");
        Files.writeString(unformatted, "public class Messy{void go(){int x=1;}}");

        var alreadyFormatted = tempDir.resolve("Clean.java");
        Files.writeString(alreadyFormatted, CLEAN_CONTENT);

        var report = DirectoryFormatter.format(tempDir);

        assertEquals(2, report.totalFiles());
        assertEquals(1, report.changed().size());
        assertEquals(unformatted, report.changed().get(0));
        assertTrue(report.failures().isEmpty());

        var expectedFormatted = """
                public class Messy {
                    void go() {
                        int x = 1;
                    }
                }""";
        assertEquals(expectedFormatted, Files.readString(unformatted));
        assertEquals(CLEAN_CONTENT, Files.readString(alreadyFormatted));
    }

    @Test
    @DisplayName("check() reports unformatted files without writing changes")
    void checkReportsWithoutWriting(@TempDir Path tempDir) throws IOException {
        var unformatted = tempDir.resolve("Messy.java");
        var originalContent = "public class Messy{void go(){int x=1;}}";
        Files.writeString(unformatted, originalContent);

        var report = DirectoryFormatter.check(tempDir);

        assertEquals(1, report.totalFiles());
        assertEquals(1, report.changed().size());
        assertEquals(unformatted, report.changed().get(0));
        assertTrue(report.failures().isEmpty());
        assertEquals(originalContent, Files.readString(unformatted));
    }

    @Test
    @DisplayName("check() reports no changes when all files are already formatted")
    void checkReportsNoChangesWhenAllFormatted(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Clean.java"), CLEAN_CONTENT);

        var report = DirectoryFormatter.check(tempDir);

        assertEquals(1, report.totalFiles());
        assertTrue(report.changed().isEmpty());
        assertTrue(report.failures().isEmpty());
    }

    @Test
    @DisplayName("format() on an empty directory reports zero files")
    void formatOnEmptyDirectory(@TempDir Path tempDir) throws IOException {
        var report = DirectoryFormatter.format(tempDir);

        assertEquals(0, report.totalFiles());
        assertTrue(report.changed().isEmpty());
        assertTrue(report.failures().isEmpty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.intellij.formatter.core.DirectoryFormatterTest"`
Expected: compilation failure — `DirectoryFormatter` and `FormatReport` do not exist yet.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/intellij/formatter/core/FormatReport.java`:

```java
package com.intellij.formatter.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Result of a {@link DirectoryFormatter#format} or {@link DirectoryFormatter#check} run.
 *
 * @param totalFiles number of {@code .java} files found under the directory
 * @param changed    files that were reformatted (format mode) or are not
 *                    currently formatted correctly (check mode)
 * @param failures   files that threw a {@link FormattingException}, mapped
 *                    to that exception's message; processing continued past them
 */
public record FormatReport(int totalFiles, List<Path> changed, Map<Path, String> failures) {
}
```

Create `src/main/java/com/intellij/formatter/core/DirectoryFormatter.java`:

```java
package com.intellij.formatter.core;

import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Runs every {@code .java} file under a directory through {@link StandaloneFormatter},
 * either writing changes back ({@link #format}) or only reporting them ({@link #check}).
 */
@UtilityClass
public class DirectoryFormatter {

    /**
     * Reformats every {@code .java} file found under {@code directory} in place,
     * writing back only files whose content actually changed.
     */
    public static FormatReport format(Path directory) throws IOException {
        return run(directory, true);
    }

    /**
     * Checks every {@code .java} file found under {@code directory} against its
     * formatted form, without writing any changes.
     */
    public static FormatReport check(Path directory) throws IOException {
        return run(directory, false);
    }

    private static FormatReport run(Path directory, boolean write) throws IOException {
        var files = JavaFileTraverser.findJavaFiles(directory);
        var changed = new ArrayList<Path>();
        var failures = new LinkedHashMap<Path, String>();

        for (var file : files) {
            var original = Files.readString(file);
            try {
                var formatted = StandaloneFormatter.formatCode(original, file.getFileName().toString());
                if (!formatted.equals(original)) {
                    changed.add(file);
                    if (write) {
                        Files.writeString(file, formatted);
                    }
                }
            } catch (FormattingException e) {
                failures.put(file, e.getMessage());
            }
        }

        return new FormatReport(files.size(), changed, failures);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.intellij.formatter.core.DirectoryFormatterTest"`
Expected: all 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/intellij/formatter/core/FormatReport.java src/main/java/com/intellij/formatter/core/DirectoryFormatter.java src/test/java/com/intellij/formatter/core/DirectoryFormatterTest.java
git commit -m "Add DirectoryFormatter for directory-wide format and check runs"
```

---

### Task 3: Rewire the CLI (`JetbrainsFormatterApplication`)

**Files:**
- Modify: `src/main/java/com/intellij/formatter/JetbrainsFormatterApplication.java` (full rewrite of `main`, `printUsage`, `printHelp`; class-level Javadoc)

**Interfaces:**
- Consumes: `DirectoryFormatter.format(Path)`, `DirectoryFormatter.check(Path)`, `FormatReport` (Task 2); existing `CodeStyleLoader.loadFromFile(String)`, `FormatterBootstrap.initialize()`.
- Produces: the `idea-format` CLI contract described below — no other task depends on this one's internals.

CLI contract after this task:

```
idea-format [OPTIONS] <directory>

OPTIONS:
    -s, --style <path>  Load IntelliJ code style from XML file
    --check              Check formatting without writing changes
    -h, --help
    -v, --version

EXIT CODES:
    0   success (all formatted / already compliant)
    1   --check found non-compliant files, no hard errors
    2   hard error (bad directory, or a file failed to parse/format)
```

- [ ] **Step 1: Replace the application source**

Replace the full contents of `src/main/java/com/intellij/formatter/JetbrainsFormatterApplication.java`:

```java
package com.intellij.formatter;

import com.intellij.formatter.config.CodeStyleLoader;
import com.intellij.formatter.core.CodeStyleLoadException;
import com.intellij.formatter.core.DirectoryFormatter;
import com.intellij.formatter.core.FormatReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.formatter.bootstrap.FormatterBootstrap.initialize;

/**
 * Command-line application for formatting Java source files using IntelliJ IDEA's
 * formatting engine.
 *
 * <p>This application recursively formats (or checks) every {@code .java} file
 * under a directory, using IntelliJ's native formatting engine.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * java -jar formatter.jar [options] <directory>
 *
 * Options:
 *   --style, -s <path>    Load IntelliJ code style from XML file
 *   --check               Check formatting without writing changes
 *   --help, -h            Show this help message
 * }</pre>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * # Format every .java file under src/main/java
 * java -jar formatter.jar src/main/java
 *
 * # Format with custom code style
 * java -jar formatter.jar --style codestyle.xml src/main/java
 *
 * # Verify formatting without writing changes (for CI)
 * java -jar formatter.jar --check src/main/java
 * }</pre>
 *
 * @see DirectoryFormatter
 * @see CodeStyleLoader
 */
public final class JetbrainsFormatterApplication {

    private static final String VERSION = "2025.3.2";
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_NOT_FORMATTED = 1;
    private static final int EXIT_ERROR = 2;

    private JetbrainsFormatterApplication() {
        // Application entry point - prevent instantiation
    }

    /**
     * Main entry point for the command-line formatter.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(EXIT_ERROR);
        }

        String directoryPath = null;
        String stylePath = null;
        var checkOnly = false;

        for (var i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    printHelp();
                    System.exit(EXIT_SUCCESS);
                }
                case "--version", "-v" -> {
                    System.out.println("idea-format " + VERSION);
                    System.exit(EXIT_SUCCESS);
                }
                case "--check" -> checkOnly = true;
                case "--style", "-s" -> {
                    if (i + 1 < args.length) {
                        stylePath = args[++i];
                    }
                }
                default -> {
                    if (!args[i].startsWith("-")) {
                        directoryPath = args[i];
                    }
                }
            }
        }

        if (directoryPath == null) {
            System.err.println("Error: No directory specified");
            printUsage();
            System.exit(EXIT_ERROR);
        }

        var directory = Path.of(directoryPath);
        if (!Files.isDirectory(directory)) {
            System.err.println("Error: Not a directory: " + directoryPath);
            System.exit(EXIT_ERROR);
        }

        try {
            System.err.println("Initializing IntelliJ formatting engine...");
            initialize();
            System.err.println("Engine initialized");

            if (stylePath != null) {
                System.err.println("Loading code style from: " + stylePath);
                try {
                    CodeStyleLoader.loadFromFile(stylePath);
                    System.err.println("Code style loaded successfully");
                } catch (CodeStyleLoadException e) {
                    System.err.println("Warning: Failed to load code style: " + e.getMessage());
                }
            }

            System.err.println((checkOnly ? "Checking: " : "Formatting: ") + directoryPath);
            var report = checkOnly ? DirectoryFormatter.check(directory) : DirectoryFormatter.format(directory);
            System.exit(printReportAndComputeExitCode(report, checkOnly));
        } catch (IOException e) {
            System.err.println("IO error: " + e.getMessage());
            System.exit(EXIT_ERROR);
        }
    }

    private static int printReportAndComputeExitCode(FormatReport report, boolean checkOnly) {
        if (checkOnly) {
            report.changed().forEach(System.out::println);
            System.out.println(report.changed().size() + " of " + report.totalFiles()
                    + " files are not formatted correctly");
        } else {
            report.changed().forEach(path -> System.out.println("Formatted: " + path));
            System.out.println("Formatted " + report.changed().size() + " of " + report.totalFiles() + " files");
        }

        report.failures().forEach((path, message) -> System.err.println("Failed: " + path + " - " + message));

        if (!report.failures().isEmpty()) {
            return EXIT_ERROR;
        }
        if (checkOnly && !report.changed().isEmpty()) {
            return EXIT_NOT_FORMATTED;
        }
        return EXIT_SUCCESS;
    }

    private static void printUsage() {
        System.err.println("Usage: idea-format [options] <directory>");
        System.err.println("Try 'idea-format --help' for more information.");
    }

    private static void printHelp() {
        System.out.println("idea-format " + VERSION + " - IntelliJ IDEA Code Formatter");
        System.out.println();
        System.out.println("Recursively formats .java files under a directory using IntelliJ IDEA's formatting engine.");
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("    idea-format [OPTIONS] <directory>");
        System.out.println();
        System.out.println("ARGUMENTS:");
        System.out.println("    <directory>          Directory to search recursively for .java files");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("    -s, --style <path>  Load IntelliJ code style from XML file");
        System.out.println("    --check              Check formatting without writing changes");
        System.out.println("    -h, --help           Show this help message");
        System.out.println("    -v, --version        Show version information");
        System.out.println();
        System.out.println("EXCLUDED DIRECTORIES (skipped at any depth):");
        System.out.println("    .git, build, target, out, node_modules");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("    idea-format src/main/java");
        System.out.println("    idea-format --check src/main/java");
        System.out.println("    idea-format --style codestyle.xml src/main/java");
        System.out.println();
        System.out.println("EXIT CODES:");
        System.out.println("    0    Success (all formatted, or all already compliant)");
        System.out.println("    1    --check found files that are not formatted correctly");
        System.out.println("    2    Error (bad directory, or a file failed to parse/format)");
        System.out.println();
        System.out.println("NOTES:");
        System.out.println("    - Requires Java 21 or later");
        System.out.println("    - First run may take 2-3 seconds for JVM warmup");
    }
}
```

- [ ] **Step 2: Build the fat JAR**

Run: `./gradlew fatJar`
Expected: `BUILD SUCCESSFUL`, producing `build/libs/vscode-idea-code-formatter.jar`.

- [ ] **Step 3: Manually verify format mode**

```bash
mkdir -p /tmp/idea-format-check/nested
printf 'public class A{void m(){int x=1;}}' > /tmp/idea-format-check/A.java
printf 'public class B {\n    void m() {\n    }\n}' > /tmp/idea-format-check/nested/B.java

java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar build/libs/vscode-idea-code-formatter.jar /tmp/idea-format-check
echo "exit code: $?"
cat /tmp/idea-format-check/A.java
```

Expected: exit code `0`; stdout lists `Formatted: /tmp/idea-format-check/A.java` and a summary `Formatted 1 of 2 files`; `A.java` on disk is now reformatted (`public class A {` on its own concerns, body indented); `nested/B.java` is unchanged since it was already compliant.

- [ ] **Step 4: Manually verify check mode**

```bash
printf 'public class C{void m(){}}' > /tmp/idea-format-check/C.java

java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar build/libs/vscode-idea-code-formatter.jar --check /tmp/idea-format-check
echo "exit code: $?"
cat /tmp/idea-format-check/C.java
```

Expected: exit code `1`; stdout lists `/tmp/idea-format-check/C.java` and `1 of 3 files are not formatted correctly`; `C.java` on disk is byte-for-byte unchanged (check mode never writes).

- [ ] **Step 5: Manually verify error exit codes**

```bash
java -jar build/libs/vscode-idea-code-formatter.jar /tmp/idea-format-check/does-not-exist
echo "exit code: $?"   # expect 2

java -jar build/libs/vscode-idea-code-formatter.jar
echo "exit code: $?"   # expect 2 (no directory specified)

rm -rf /tmp/idea-format-check
```

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew test`
Expected: all tests, including Tasks 1 and 2's new tests, PASS. No test references the removed `--lines` CLI behavior (it was never covered by an existing test — confirm with `grep -rn "lines" src/test/`, expecting no CLI-level match).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/intellij/formatter/JetbrainsFormatterApplication.java
git commit -m "Rewire CLI to format/check directories of .java files instead of a single file"
```

---

### Task 4: Update docs (README, wrapper script header, CHANGELOG)

**Files:**
- Modify: `README.md`
- Modify: `scripts/idea-format` (header comment only)
- Modify: `CHANGELOG.md`

**Interfaces:** None — documentation only, no code depends on this task.

- [ ] **Step 1: Update `scripts/idea-format`'s header comment**

In `scripts/idea-format`, replace the header comment block:

```bash
#!/usr/bin/env bash
#
# idea-format - IntelliJ IDEA Code Formatter CLI
#
# Recursively formats all .java files under a directory using IntelliJ
# IDEA's formatting engine.
#
# Usage:
#   idea-format [options] <directory>
#
# Options:
#   -s, --style <path>    Load IntelliJ code style from XML file
#   --check               Check formatting without writing changes
#   -h, --help            Show help message
#   -v, --version         Show version information
#
```

(This replaces the existing header comment lines 1-16; the `set -e` and everything below is unchanged.)

- [ ] **Step 2: Update `README.md`**

Replace the "Features" bullet about line ranges (line 9) — remove:
```
- **Line Range Formatting**: Format specific lines instead of entire files
```
with:
```
- **Directory-Wide**: Recursively formats every `.java` file under a directory
- **CI-Friendly Check Mode**: Verify formatting without writing changes, with exit codes for CI gating
```

Replace the entire "### 2. Format Files" section (original lines 47-76) with:

```markdown
### 2. Format Files

Using the wrapper script:

```bash
# Format every .java file under a directory (recursively)
./scripts/idea-format src/main/java

# Check formatting without writing changes (exit 1 if anything is non-compliant)
./scripts/idea-format --check src/main/java

# Format with custom code style
./scripts/idea-format --style my-codestyle.xml src/main/java
```

`.git`, `build`, `target`, `out`, and `node_modules` directories are skipped
automatically, at any depth.

Or run directly with Java:

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
     --add-opens java.base/java.io=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar build/libs/vscode-idea-code-formatter.jar src/main/java
```
```

Replace the "Custom Code Style" example (original lines 78-85) `./scripts/idea-format --style my-codestyle.xml path/to/MyClass.java` with `./scripts/idea-format --style my-codestyle.xml src/main/java`.

In the "Programmatic Usage" section, remove the line-range example from the code block (original lines 114-116):
```java
        // Format specific lines (1-based, inclusive)
        String rangeFormatted = StandaloneFormatter.formatCodeRange(
            javaCode, "Test.java", 1, 5);
```
(`StandaloneFormatter.formatCodeRange` is unchanged and still part of the library API — this only removes it from the CLI-focused walkthrough. Leave the rest of the programmatic example as-is.)

Update the "Project Architecture" file tree (original lines 169-172) to add the two new `core` classes:
```
│       ├── core/
│       │   ├── StandaloneFormatter.java        # Main formatting API
│       │   ├── JavaFileTraverser.java          # Recursive .java file discovery
│       │   ├── DirectoryFormatter.java         # Directory-wide format/check runs
│       │   ├── FormatReport.java               # Format/check run results
│       │   ├── FormattingException.java        # Formatting errors
│       │   └── CodeStyleLoadException.java     # Config loading errors
```

Update the Gradle run examples (original lines 148-149):
```bash
./gradlew run --args="src/main/java"
./gradlew run --args="--check src/main/java"
```

Update the CI/CD example (original lines 249-263) to use check mode against a directory:

```yaml
- name: Setup Java
  uses: actions/setup-java@v3
  with:
    java-version: '21'
    distribution: 'temurin'

- name: Check Formatting
  run: |
    java --add-opens java.base/java.lang=ALL-UNNAMED \
         --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
         --add-opens java.base/java.io=ALL-UNNAMED \
         --add-opens java.base/java.util=ALL-UNNAMED \
         -jar vscode-idea-code-formatter.jar --check src/main/java
```

- [ ] **Step 3: Add a `CHANGELOG.md` entry**

Insert a new section above the existing `## [2025.3.2] - 2026-02-05` entry:

```markdown
## [Unreleased]

### Changed
- CLI now takes a directory instead of a single file, recursively formatting
  every `.java` file found (skipping `.git`, `build`, `target`, `out`, and
  `node_modules` at any depth)
- Removed `--lines` CLI option (formatting a line range within one file no
  longer composes with directory-wide operation); `StandaloneFormatter.formatCodeRange`
  is still available for programmatic use

### Added
- `--check` CLI flag: verifies formatting without writing changes, for use
  as a CI gate (exit code `1` if any file is not formatted correctly)

```

- [ ] **Step 4: Commit**

```bash
git add README.md scripts/idea-format CHANGELOG.md
git commit -m "Update docs for directory-wide CLI and --check mode"
```
