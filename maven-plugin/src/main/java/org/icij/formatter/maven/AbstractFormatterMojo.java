package org.icij.formatter.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
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
}
