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
