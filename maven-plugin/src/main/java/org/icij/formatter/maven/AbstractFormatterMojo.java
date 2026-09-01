package org.icij.formatter.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractFormatterMojo extends AbstractMojo {

    static final List<String> ADD_OPENS_FLAGS = List.of(
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/javax.swing=ALL-UNNAMED"
    );

    /** Classpath location of the core jar embedded at build time by maven-dependency-plugin. */
    static final String EMBEDDED_CORE_JAR = "/intellij-code-formatter.jar";

    @Parameter(defaultValue = "${project.build.sourceDirectory}", required = true)
    File directory;

    @Parameter
    File codeStyle;

    @Parameter(property = "formatter.skip", defaultValue = "false")
    boolean skip;

    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    PluginDescriptor pluginDescriptor;

    /**
     * Where the extracted core jar is cached. Sitting inside the local repository means a
     * CI job that already caches {@code ~/.m2} gets the extracted jar for free, and it
     * honours {@code -Dmaven.repo.local}.
     */
    @Parameter(defaultValue = "${settings.localRepository}", readonly = true, required = true)
    File localRepository;

    abstract boolean checkOnly();

    /**
     * @param exitCode the forked formatter's exit code
     * @param report   what the forked formatter wrote to stdout: the files it changed, or
     *                 that are not formatted correctly, plus a summary line
     */
    abstract void handleExitCode(int exitCode, List<String> report)
            throws MojoExecutionException, MojoFailureException;

    static List<String> buildCommand(String javaExecutable, File coreJar, File codeStyleFile,
                                      boolean checkOnly, File directory) {
        var command = new ArrayList<String>();
        command.add(javaExecutable);
        command.addAll(ADD_OPENS_FLAGS);
        command.add("-Djava.awt.headless=true");
        command.add("-jar");
        command.add(coreJar.getAbsolutePath());
        command.add("--style");
        command.add(codeStyleFile.getAbsolutePath());
        if (checkOnly) {
            command.add("--check");
        }
        command.add(directory.getAbsolutePath());
        return command;
    }

    File resolveCodeStyle() throws MojoExecutionException {
        if (codeStyle != null) {
            if (!codeStyle.isFile()) {
                throw new MojoExecutionException("codeStyle file not found: " + codeStyle);
            }
            return codeStyle;
        }

        try (InputStream in = getClass().getResourceAsStream("/icij-codestyle.xml")) {
            if (in == null) {
                throw new MojoExecutionException("Bundled /icij-codestyle.xml resource not found on the plugin classpath");
            }
            var tempFile = File.createTempFile("icij-codestyle", ".xml");
            tempFile.deleteOnExit();
            Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to extract the bundled codestyle", e);
        }
    }

    /**
     * Path the core jar is cached at. Keyed by plugin version so that upgrading the plugin
     * never reuses a stale core.
     */
    Path coreJarCachePath() {
        return localRepository.toPath()
                .resolve(".cache")
                .resolve("icij-formatter")
                .resolve("intellij-code-formatter-" + pluginDescriptor.getVersion() + ".jar");
    }

    File resolveCoreJar() throws MojoExecutionException {
        var target = coreJarCachePath();
        if (Files.isRegularFile(target)) {
            return target.toFile();
        }

        try (InputStream in = getClass().getResourceAsStream(EMBEDDED_CORE_JAR)) {
            if (in == null) {
                throw new MojoExecutionException(
                        "Embedded " + EMBEDDED_CORE_JAR + " not found on the plugin classpath");
            }
            getLog().info("Extracting the formatter core to " + target + " (first run only)");
            return extractToCache(in, target).toFile();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to extract the embedded formatter core jar", e);
        }
    }

    /**
     * Copies {@code in} to {@code target} at most once, atomically.
     *
     * <p>Returns immediately when {@code target} already exists, so every execution after
     * the first - other modules of the same reactor, and every later build - costs a single
     * {@code stat} rather than re-writing the jar. The copy lands on a sibling {@code .part}
     * file first (same filesystem, so the move stays atomic): without that, a concurrent
     * build could fork {@code java -jar} on a half-written jar and fail with an opaque
     * {@code ZipException}.</p>
     */
    static Path extractToCache(InputStream in, Path target) throws IOException {
        if (Files.isRegularFile(target)) {
            return target;
        }

        Files.createDirectories(target.getParent());
        var part = Files.createTempFile(target.getParent(), "core-", ".part");
        try {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            // A concurrent build won the race. Its copy is identical, so keep it.
            Files.deleteIfExists(part);
        } catch (IOException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        return target;
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping intellij-code-formatter (formatter.skip=true)");
            return;
        }
        if (directory == null || !directory.isDirectory()) {
            getLog().info("Skipping intellij-code-formatter: " + directory + " is not a directory");
            return;
        }

        var coreJar = resolveCoreJar();
        var codeStyleFile = resolveCodeStyle();
        var javaExecutable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        var command = buildCommand(javaExecutable, coreJar, codeStyleFile, checkOnly(), directory);

        var result = runProcess(command);
        handleExitCode(result.exitCode(), result.report());
    }

    /**
     * @param exitCode the forked formatter's exit code
     * @param report   the lines it wrote to stdout
     */
    record ProcessResult(int exitCode, List<String> report) {
    }

    /**
     * Runs the forked formatter, logging everything it prints and returning its report.
     *
     * <p>stdout and stderr are kept apart rather than merged: stdout carries the report
     * - the offending or reformatted files - which callers need in order to name those
     * files in a failure message, while stderr only carries progress. stderr is drained
     * on its own thread because a full pipe buffer would otherwise block the forked
     * process while we sit reading the other stream.</p>
     */
    private ProcessResult runProcess(List<String> command) throws MojoExecutionException {
        try {
            var process = new ProcessBuilder(command).start();
            var progress = Thread.ofVirtual().start(() -> logProgress(process));

            List<String> report;
            try (BufferedReader reader = process.inputReader()) {
                report = reader.lines().peek(getLog()::info).toList();
            }
            var exitCode = process.waitFor();
            progress.join();
            return new ProcessResult(exitCode, report);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to run the formatter process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Formatter process interrupted", e);
        }
    }

    private void logProgress(Process process) {
        try (BufferedReader reader = process.errorReader()) {
            reader.lines().forEach(getLog()::info);
        } catch (IOException e) {
            getLog().debug("Failed to read the formatter process stderr", e);
        }
    }
}
