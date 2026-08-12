package org.icij.formatter.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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

    @Parameter(defaultValue = "${project.build.sourceDirectory}", required = true)
    File directory;

    @Parameter
    File codeStyle;

    @Parameter(property = "formatter.skip", defaultValue = "false")
    boolean skip;

    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    PluginDescriptor pluginDescriptor;

    abstract boolean checkOnly();

    abstract void handleExitCode(int exitCode) throws MojoExecutionException, MojoFailureException;

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

    File resolveCoreJar() throws MojoExecutionException {
        for (Artifact artifact : pluginDescriptor.getArtifacts()) {
            if ("org.icij".equals(artifact.getGroupId()) && "intellij-code-formatter".equals(artifact.getArtifactId())) {
                return artifact.getFile();
            }
        }
        throw new MojoExecutionException(
                "Could not resolve org.icij:intellij-code-formatter among this plugin's dependencies");
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

        var exitCode = runProcess(command);
        handleExitCode(exitCode);
    }

    private int runProcess(List<String> command) throws MojoExecutionException {
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader reader = process.inputReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    getLog().info(line);
                }
            }
            return process.waitFor();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to run the formatter process", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MojoExecutionException("Formatter process interrupted", e);
        }
    }
}
