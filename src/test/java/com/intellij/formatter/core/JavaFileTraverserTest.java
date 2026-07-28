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
