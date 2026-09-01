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
                    } else {
                        System.err.println("Error: --style requires a value");
                        printUsage();
                        System.exit(EXIT_ERROR);
                    }
                }
                case "--lines" -> {
                    System.err.println("Error: --lines was removed; the CLI now formats/checks "
                            + "whole directories. See CHANGELOG.md.");
                    System.exit(EXIT_ERROR);
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        System.err.println("Error: Unknown option: " + args[i]);
                        printUsage();
                        System.exit(EXIT_ERROR);
                    } else if (directoryPath == null) {
                        directoryPath = args[i];
                    } else {
                        System.err.println("Error: Multiple directories specified: "
                                + directoryPath + " and " + args[i]);
                        printUsage();
                        System.exit(EXIT_ERROR);
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
        System.out.println("    2    Error (bad directory, or a file failed to read/format)");
        System.out.println();
        System.out.println("NOTES:");
        System.out.println("    - Requires Java 21 or later");
        System.out.println("    - First run may take 2-3 seconds for JVM warmup");
    }
}
