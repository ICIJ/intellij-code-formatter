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
