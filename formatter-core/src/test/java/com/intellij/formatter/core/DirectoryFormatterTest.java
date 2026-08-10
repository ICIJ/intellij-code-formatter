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

    @Test
    @DisplayName("format() records I/O failures without aborting the run")
    void formatRecordsIoFailuresAndContinues(@TempDir Path tempDir) throws IOException {
        var badFile = tempDir.resolve("Bad.java");
        Files.write(badFile, new byte[] { (byte) 0xFF, (byte) 0xFE });

        var goodFile = tempDir.resolve("Zzz.java");
        Files.writeString(goodFile, "public class Zzz{void go(){int x=1;}}");

        var report = DirectoryFormatter.format(tempDir);

        assertEquals(2, report.totalFiles());
        assertEquals(1, report.failures().size());
        assertTrue(report.failures().containsKey(badFile));
        assertEquals(java.util.List.of(goodFile), report.changed());
    }
}
