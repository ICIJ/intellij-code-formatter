package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;

@Mojo(name = "format", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class FormatMojo extends AbstractFormatterMojo {

    @Override
    boolean checkOnly() {
        return false;
    }

    @Override
    void handleExitCode(int exitCode, List<String> report) throws MojoExecutionException {
        if (exitCode != 0) {
            throw new MojoExecutionException("Formatter process failed with exit code " + exitCode);
        }
        getLog().info("Formatting complete");
    }
}
