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
            try {
                var original = Files.readString(file);
                var formatted = StandaloneFormatter.formatCode(original, file.getFileName().toString());
                if (!formatted.equals(original)) {
                    changed.add(file);
                    if (write) {
                        Files.writeString(file, formatted);
                    }
                }
            } catch (FormattingException e) {
                failures.put(file, e.getMessage());
            } catch (IOException e) {
                failures.put(file, "I/O error: " + e.getMessage());
            }
        }

        return new FormatReport(files.size(), changed, failures);
    }
}
