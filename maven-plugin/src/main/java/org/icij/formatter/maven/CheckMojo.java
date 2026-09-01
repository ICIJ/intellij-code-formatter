package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;

@Mojo(name = "check", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class CheckMojo extends AbstractFormatterMojo {

    @Override
    boolean checkOnly() {
        return true;
    }

    @Override
    void handleExitCode(int exitCode, List<String> report) throws MojoExecutionException, MojoFailureException {
        switch (exitCode) {
            case 0 -> getLog().info("All files are correctly formatted");
            case 1 -> throw new MojoFailureException(failureMessage(report));
            default -> throw new MojoExecutionException("Formatter process failed with exit code " + exitCode);
        }
    }

    /**
     * Names the offending files in the failure itself.
     *
     * <p>The report is also logged line by line, but at INFO, which Maven drops under
     * {@code -q} and under the quiet log levels CI jobs tend to use - leaving whoever
     * broke the build with nothing but "some files are not formatted correctly". The
     * failure message is the one channel that always reaches the console.</p>
     */
    static String failureMessage(List<String> report) {
        var message = new StringBuilder(
                "Some files are not formatted correctly; run the `format` goal to fix them:");
        report.forEach(line -> message.append(System.lineSeparator()).append("  ").append(line));
        return message.toString();
    }
}
