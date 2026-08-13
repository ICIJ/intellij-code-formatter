package org.icij.formatter.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;

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
        assertDoesNotThrow(() -> new CheckMojo().handleExitCode(0));
    }

    @Test
    void handleExitCode_one_throwsMojoFailureException() {
        assertThrows(MojoFailureException.class, () -> new CheckMojo().handleExitCode(1));
    }

    @Test
    void handleExitCode_two_throwsMojoExecutionException() {
        assertThrows(MojoExecutionException.class, () -> new CheckMojo().handleExitCode(2));
    }
}
