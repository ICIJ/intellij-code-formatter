package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "check", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class CheckMojo extends AbstractFormatterMojo {

    @Override
    boolean checkOnly() {
        return true;
    }

    @Override
    void handleExitCode(int exitCode) throws MojoExecutionException, MojoFailureException {
        switch (exitCode) {
            case 0 -> getLog().info("All files are correctly formatted");
            case 1 -> throw new MojoFailureException(
                    "Some files are not formatted correctly; run the `format` goal to fix them");
            default -> throw new MojoExecutionException("Formatter process failed with exit code " + exitCode);
        }
    }
}
