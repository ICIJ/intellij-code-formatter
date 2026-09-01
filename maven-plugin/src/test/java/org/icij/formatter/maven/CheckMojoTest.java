package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckMojoTest {

    @Test
    void checkOnly_isTrue() {
        assertTrue(new CheckMojo().checkOnly());
    }

    @Test
    void handleExitCode_zero_succeeds() {
        assertDoesNotThrow(() -> new CheckMojo().handleExitCode(0, List.of()));
    }

    @Test
    void handleExitCode_one_throwsMojoFailureException() {
        assertThrows(MojoFailureException.class, () -> new CheckMojo().handleExitCode(1, List.of()));
    }

    @Test
    void handleExitCode_one_failureMessageNamesTheOffendingFiles() {
        var report = List.of("/src/main/java/A.java", "/src/main/java/B.java",
                "2 of 5 files are not formatted correctly");

        var failure = assertThrows(MojoFailureException.class,
                () -> new CheckMojo().handleExitCode(1, report));

        // The report is only logged at INFO, which -q drops: the failure message is the
        // one place the file list is guaranteed to reach the console.
        report.forEach(line -> assertTrue(failure.getMessage().contains(line),
                "expected the failure message to contain " + line + ", was:\n" + failure.getMessage()));
    }

    @Test
    void handleExitCode_two_throwsMojoExecutionException() {
        assertThrows(MojoExecutionException.class, () -> new CheckMojo().handleExitCode(2, List.of()));
    }
}
